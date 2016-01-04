package com.example.fabric8

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import argonaut.Parse
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.StrictLogging
import io.fabric8.kubernetes.api.model._
import io.fabric8.kubernetes.client.{DefaultKubernetesClient, KubernetesClientException, Watcher}
import io.fabric8.kubernetes.client.Watcher.Action

import scala.collection.JavaConversions._
import scala.io.{Codec, Source}


import com.typesafe.scalalogging.StrictLogging
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.DefaultKubernetesClient.ConfigBuilder

class KubeClient(kubeServer: String = "http://localhost:8080") extends StrictLogging {
  val configBuilder = new ConfigBuilder()
  val config = configBuilder.masterUrl(kubeServer).build()
  def createClient() = {
    logger.info(s"starting kubernetes client at $kubeServer")
    new DefaultKubernetesClient(config)
  }
}

class ClusterManager(kubeClient: KubeClient) extends StrictLogging {
  private def isPodComplete(pod: Pod) = {
    pod.getStatus.getConditions.toList
      .find { condition => condition.getStatus.toLowerCase == "true" && condition.getType.toLowerCase == "ready" } match {
      case Some(condition) => true
      case None => false
    }
  }

  private def isRcComplete(rc: ReplicationController) = { rc.getSpec.getReplicas == rc.getStatus.getReplicas }

  private def isServiceComplete(service: Service) = { !service.getStatus.getLoadBalancer.getIngress.isEmpty }

  private def watchPods(namespace: String, name: String) = {
    logger.debug("start -> watching pods -> namespace: " + namespace + " name: " + name)

    val kube = kubeClient.createClient()
    try {
      @volatile var complete = false
      val socket = kube.pods().inNamespace(namespace).withLabel("name", name).watch(new Watcher[Pod]() {
        def eventReceived(action: Action, resource: Pod) {
          logger.info(action + ":" + resource)
          action match {
            case Action.MODIFIED =>
              complete = isPodComplete(resource)
            case _ => case _ => logger.debug(s"watchPods - received event for unhandled action $action / $resource")
          }
        }
      })
      while (!complete) {
        logger.debug("in watch pods sleep loop")
        Thread.sleep(2000)
      }
      socket.close()
    } finally {
      kube.close()
    }
    logger.debug("complete -> watching pods -> namespace: " + namespace + " name: " + name)
  }

  private def watchRc(namespace: String, name: String) = {
    logger.debug("start -> watching rc -> namespace: " + namespace + " name: " + name)

    val kube = kubeClient.createClient()
    try {
      @volatile var complete = false
      val socket = kube.replicationControllers().inNamespace(namespace).withName(name).watch(new Watcher[ReplicationController]() {
        def eventReceived(action: Action, resource: ReplicationController) {
          logger.info(action + ":" + resource)
          action match {
            case Action.MODIFIED =>
              complete = isRcComplete(resource)
            case _ => logger.debug(s"watchRc - received event for unhandled action $action / $resource")
          }
        }
      })
      while (!complete) {
        logger.debug("in watch rc sleep loop")
        Thread.sleep(2000)
      }
      socket.close()
    } finally {
      kube.close()
    }
    logger.debug("complete -> watching rc -> namespace: " + namespace + " name: " + name)
  }

  private def watchService(service: Service) = {
    val namespace = service.getMetadata.getNamespace
    val name = service.getMetadata.getName
    logger.debug("start -> watching service -> namespace: " + namespace + " name: " + name)
    val kube = kubeClient.createClient()
    try {
      @volatile var complete = false
      val socket = kube.services().inNamespace(namespace).withName(name).watch(new Watcher[Service]() {
        def eventReceived(action: Action, resource: Service) {
          logger.info(action + ":" + resource)
          action match {
            case Action.MODIFIED =>
              if (resource.getMetadata.getName == name) {
                complete = isServiceComplete(resource)
              }
            //            case Action.DELETED =>
            //              complete = true
            case _ => logger.debug(s"watchService - received event for unhandled action $action / $resource")
          }
        }
      })
      while (!complete) {
        logger.debug("in watch pods sleep loop")
        Thread.sleep(2000)
        complete = isServiceComplete(kube.services().inNamespace(namespace).withName(name).get)
      }
      logger.info("Closing socket connection")
      socket.close()
    } finally {
      logger.info("Closing client connection")
      kube.close()
    }

    logger.debug("complete -> watching services , namespace: " + namespace + " name: " + name)
  }

  def createComponent(componentAsJson: String, namespace: String = "default"): Unit = {
    val mapper = new ObjectMapper()
    val kube = kubeClient.createClient()
    val result = Parse.parse(componentAsJson)
    val json =
      result.valueOr { errs => throw new RuntimeException("Sorry! I am unable to parse kubernetes spec " + errs) }
    val clazz = json.field("kind") match {
      case Some(kind) =>
        kind.string.get match {
          case "Pod" =>
            val pod = mapper.readValue(componentAsJson, classOf[Pod])
            kube.pods().inNamespace(namespace).create(pod)
            watchPods(namespace, pod.getMetadata.getName)
          case "ReplicationController" =>
            val rc = mapper.readValue(componentAsJson, classOf[ReplicationController])
            kube.replicationControllers().inNamespace(namespace).create(rc)
            watchRc(namespace, rc.getMetadata.getName)
          case "Service" =>
            val serviceConf = mapper.readValue(componentAsJson, classOf[Service])
            val service = kube.services().inNamespace(namespace).create(serviceConf)
            watchService(service)
          case other => throw new RuntimeException("Sorry I cannot handle the kind " + other.toString)
        }
      case None =>
        throw new RuntimeException("I am unable to parse kind from the kubernetes spec")
    }

  }
}