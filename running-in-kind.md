# Running the demo app in Kind

#### 1. Install Podman
```
yum install podman -y
```

#### 2. Install yq
```
wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/bin/yq && chmod +x /usr/bin/yq
```

#### 3. Install KIND

Look up the latest version of the release (https://github.com/kubernetes-sigs/kind/releases). Then use that version to run the following commands.

```
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.18.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
```

#### 4. Create a KIND cluster

Before we create the cluser we need to create a configuration file. As we want to use an ingress controller.
```
echo "
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
" > kind.config
```
now create the cluster using this config
```
sysctl net.ipv6.conf.all.disable_ipv6=0;kind create cluster --config kind.config
```
We are enabling ipv6 before creating cluster, as there is a bug that runs into an issue creating network if ipv6 is disabled.


#### 5. Create the Ingress Controller

Before we can create an Ingress, we need a controller.
```
kubectl apply --filename https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/static/provider/kind/deploy.yaml
```
Wait of pods to get ready
```
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=90s
```

#### 6. Create the application

```
kubectl create deployment liberty-demo --image quay.io/nsaini/liberty-demo-app --port 9080
```
expose the service
```
kubectl expose deployment liberty-demo
```

#### 7. Create an Ingress
```
echo "
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: liberty-demo
spec:
  rules:
    - host: ess-mq-cluster1.fyre.com
      http:
        paths:
        - pathType: Prefix
          path: /demo-app
          backend:
            service: 
              name: liberty-demo
              port:
                number: 9080
        - pathType: Prefix
          path: /hello
          backend:
            service: 
              name: hello
              port:
                number: 80
" > liberty-demo-ingress.yaml
```
The host is the DNS name of the host running the KIND cluster. You can change that to any valid dns that resolves to this HOST (as our ingress is running here).

We have another application running in our namespace exposing port 80, that is why you see another path being exposed. You can create this by running the following command.
```
kubectl run hello --expose --image nginxdemos/hello:plain-text --port 80
```

Now lets create the ingress.
```
kubectl apply -f liberty-demo-ingress.yaml
```

It takes a few seconds for the ingress to start serving. You should be able to hit the host in a browser with the url http://`hostname`/demo-app/hello to get a response from your app!
Or use curl using that endpoint.
```
curl -sw "\n" http://$(kubectl get ingress liberty-demo -o jsonpath='{.spec.rules[].host}')/demo-app/hello
```
  
