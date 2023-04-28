# Tekton Pipelines


### 1. Install Redhat OpenShift Pipelines (Tekton) Operator
Install the "Install Redhat OpenShift Pipelines" Operator in all namespaces
Install the tekton cli utility
```
dnf copr enable chmouel/tektoncd-cli
dnf install tektoncd-cli
```


### 2. Create demoapp

create the demoapp (make sure you are in the correct namespace).
```
oc new-app --name demoapp-quay --image quay.io/nsaini/liberty-demo-app:latest
```
expose the service so can be accessed using a route outside of the cluster.
```
oc expose svc demoapp
```
use a curl command to validate everything is working
```
curl -sw "\n" $(oc get routes demoapp-quay -o jsonpath="{.spec.host}")/demo-app/hello
```

### 3. Create the Pipeline

Apply the pipeline yaml. First make sure you are either in the correct project or provide the namespace
```
oc apply -f https://raw.githubusercontent.com/nmsaini/liberty-demo-app/main/tekton/demo-pipeline.yaml -n liberty 
```
view the pipeline definition.
There are 3 params and 3 workspaces.
+ Param1: git-url - the url of the git repo to clone
+ Param2: image-name - the image-name that we are building (we are assuming it lives in Quay)
+ Param3: deploy-name - the name of the deployment (app name)

+ Workspace: workspace - workspace area where the clone is done. If using a PVC then it can be shared with all stages of the pipeline
We are using a pvc for our build. You may have to adjust the StorageClassName to meet your cluster requirements.
```
oc apply -f https://raw.githubusercontent.com/nmsaini/liberty-demo-app/main/tekton/demoapp-build-pvc.yaml -n liberty
```
+ Workspace: git-creds - contains the git credentials. In our case it is the ssh-keys that we will be using to access git repo.
This is a public repo, so no creds are needed. However, I tested this with a private git repo and the instructions are as follows.
create an ssh keypair
```
ssh-keygen -f demoapp -t ed25519 -C nsaini-demoapp
```
Copy the contents of the public key (demoapp.pub) into your GitHub project settings -> deploy keys (read or r/w, I did only read as that is all that is needed).
Now copy the private key into a secret so you can mount them into your workspace as git-creds.
```
oc create secret generic git-creds --from-file id_ed25519=demoapp -n liberty
```

+ Workspace: quayconfig - contains creds to push image into quay registry
Login in to Quay.io and under your profile you will see an entry for "Robot Accounts". Create a new account. It will generate a name and password.
Use these values to create a quayconfig workspace.
```
oc create secret docker-registry quayconfig \
  --docker-server=quay.io \
  --docker-username=username \
  --docker-password=XXXX -n liberty
```

### 4. Test the Pipeline
You can test the pipeline from the OCP console. Or use the command line using the Tekton CLI that you install in Step 1.
As mentioned earlier i did test the pipeline with a private github repository just to test out my pipeline. Here is one private repo that i used.
```
tkn pipeline start demoapp-build-deploy-pipeline \
  --param git-url=git@github.ibm.com:nsaini-us/liberty-demo-app.git \
  --param image-name=liberty-demo-app \
  --param deploy-name=demoapp-quay \
  --workspace name=workspace,claimName=demoapp-build-pvc \
  --workspace name=quayconfig,secret=quayconfig \
  --workspace name=git-creds,secret=git-creds 
```

However, you can start the pipeline by not providing any params if defauls are specified in your pipeline.
```
tkn pipeline start demoapp-build-deploy-pipeline \
  --workspace name=workspace,claimName=demoapp-build-pvc \
  --workspace name=quayconfig,secret=quayconfig \
  --workspace name=git-creds,secret=git-creds \
  --use-param-defaults
```
After a successful pipeline run, you should end up with a new quay image. In the third task of the pipeline, we are simply using this command to refresh the image. 
```
oc import-image demoapp-quay --from quay.io/nsaini/liberty-demo-app:latest --confirm
```
You don't have to manually do this as the pipeline does this as the last step. Once a new image is downloaded, your pods should be restarted with the new image. 


### 5. Trigger Pipeline with a code commit

To do this integration we need to define a few more resources. 
An EventListener which listens to the incoming hooks. A trigger that takes that requests and Intercepts it and validates. Once validated, A trigger binding usees the trigger template to trigger the pipeline. Lots of places to go wrong!

```
oc apply -f https://raw.githubusercontent.com/nmsaini/liberty-demo-app/main/tekton/event-trig-template-bindings.yaml -n liberty
```

This creates 4 resources. EventListener, Trigger, TriggerBindings, and TriggerTemplate. Since most of my params are fairly constant I am not parsing anything out of the webhook. Keep it simple.

Once the eventlistener is created you will see a pod spin up. There is a corresponding service created. You will have to expose that service in order to use a webhook from outside your cluster.
```
oc expose svc el-el-demoapp
```
> :information_desk_person: for some reason the svc name is "el-" prepended to the name.

get the route 
```
oc get routes el-el-demoapp -o jsonpath="{.spec.host}"
```
In addition you will need a secret string that you configure that your webhook needs to provide, else the interceptor will reject the request (security). 
```
oc create secret generic github-secret --from-literal secretToken=<secretstring> -n liberty
```
Now go to your github project settings. Select Tab to "Hooks" or "Webhooks", hit the "Add Webhook"
Paste the url into the Payload URL. Make sure you append with "http://"
Change the content-type to "application/json" and paste the <secretstring> in the Secret.

### 6. Test
Make a change to your source code and commit. The commit should create a webhook that calls your route endpoint, that triggers your pipeline. This clones your code, builds it and deploys it!
