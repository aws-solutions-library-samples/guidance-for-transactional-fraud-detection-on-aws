
### use Kinesis data producer
The sample Kinesis producer generates sample transaction records
```
pip3 install schedule boto3 aws_requests_auth requests uuid watchtower urllib3==1.26

python ./source/samplekinesisproducer/kinesisproducer.py -r us-east-1 -k nt-fraudandrisk-source-stream

```