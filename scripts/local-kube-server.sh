NS=${1:-default}


kubectl config set-cluster dev --server=http://localhost:8080
kubectl config set-context dev --cluster=dev --user=svignesh --namespace=$NS
kubectl config use-context dev


