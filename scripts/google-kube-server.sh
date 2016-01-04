NS=${1:-default}


kubectl config set-cluster dev --server=http://104.154.60.144
kubectl config set-context dev --cluster=dev --namespace=$NS
kubectl config use-context dev



gcloud config set project stellar-access-117903
gcloud config set compute/zone us-central1-b
 gcloud container clusters create hello-world \
    --num-nodes 1 \
    --machine-type g1-small
