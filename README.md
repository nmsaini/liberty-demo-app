# liberty-demo-app in OpenShift

### Create a new project/namespace
`oc new-project liberty-demo`

### Create the app
`oc new-app --name demo-app https://github.com/nmsaini/liberty-demo-app.git`

### Watch the build
`oc logs bc/demo-app`

### Wait for pods to start
`oc get pods -w`

### Create a route
`oc expose service demo-app`

### Test your endpoint
```
curl -sw "\n" $(oc get routes demo-app -o jsonpath="{.spec.host}")/demo-app/hello
```

### For secure/https endpoint
```
oc create route passthrough demo-app-quay-https --service demo-app-quay --port 9443
```
```
curl --insecure -sw "\n" https://$(oc get routes demo-app-quay-https -o jsonpath="{.spec.host}")/demo-app/hello
```

you should see a response like
> Hello from demo-app-b5f99cd7d-cf97f
