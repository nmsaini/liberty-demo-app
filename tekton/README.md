# Tekton Pipelines


### 1. Install Redhat OpenShift Pipelines (Tekton) Operator
Install the "Install Redhat OpenShift Pipelines" Operator in all namespaces
Install the tekton cli utility
```
dnf copr enable chmouel/tektoncd-cli
dnf install tektoncd-cli
```


### 2. create demoapp

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

### 3. create the pipeline

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

### 4. test the pipeline
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
After a successful pipeline run, you should end up with a new quay image. This image is used in the pipeline to refresh your app image. You should see your pods restart!


### 5. setup a webhook to kick off the pipeline when a commit is made
/inc

