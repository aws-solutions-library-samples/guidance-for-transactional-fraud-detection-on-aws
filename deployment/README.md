## High Level Template Structure
The deployment of the solution is structured as a nested template hierarchy. Each template has specific resposibility


* base.yaml
  * timestreamflow.yaml
    * neptunebasestackexistinginfra
  * neptuneflow.yaml
    * athenaconnector.yaml



### Template packaging

```
aws s3api create-bucket --bucket riskandfraudlogs-{alias}-assets --region us-east-1
```

Go to root of repository folder and execute:
```
cd {home}/guidance-for-transactional-fraud-detection-on-aws
sh ./deployment/setup.sh riskandfraudlogs-{alias}-assets
```