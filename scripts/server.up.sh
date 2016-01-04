#!/bin/bash
#
#   Create single node kubernetes cluster
#   See: https://github.com/kubernetes/kubernetes/blob/release-1.1/docs/getting-started-guides/docker.md
#
#   kubernetes api server will be launched at localhost:8080


K8S_VERSION=1.1.1

sudo docker run --net=host -d gcr.io/google_containers/etcd:2.0.12 /usr/local/bin/etcd --addr=127.0.0.1:4001 --bind-addr=0.0.0.0:4001 --data-dir=/var/etcd/data


sudo docker run \
    --volume=/:/rootfs:ro \
    --volume=/sys:/sys:ro \
    --volume=/dev:/dev \
    --volume=/var/lib/docker/:/var/lib/docker:ro \
    --volume=/var/lib/kubelet/:/var/lib/kubelet:rw \
    --volume=/var/run:/var/run:rw \
    --net=host \
    --pid=host \
    --privileged=true \
    -d \
    gcr.io/google_containers/hyperkube:v${K8S_VERSION} \
    /hyperkube kubelet --containerized --hostname-override="127.0.0.1" --address="0.0.0.0" --api-servers=http://localhost:8080 --config=/etc/kubernetes/manifests

sudo docker run -d --net=host --privileged gcr.io/google_containers/hyperkube:v${K8S_VERSION} /hyperkube proxy --master=http://127.0.0.1:8080 --v=2


sleep 15

.  set-kube-context.sh

kubectl -s http://localhost:8080 get all | grep "master.*Running" > /dev/null
if [ "$?" != "0" ] ; then 
    echo something went wrong. find kubernetes master not running.
    exit 1
fi
