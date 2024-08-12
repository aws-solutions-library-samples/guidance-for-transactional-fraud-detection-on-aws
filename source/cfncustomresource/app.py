import os
import datetime as dt
import logging
from random import uniform
import json
import cfnresponse
import awswrangler as wr
import pandas as pd

logger = logging.getLogger(__name__)

def write_record():
    t = dt.datetime.now(dt.timezone.utc)
    df = pd.DataFrame([
        {
            'time': t,
            'account_num': 'A1',
            'merchant_num': 'B1',
            'Value': uniform(1,10)
        },
        {
            'time': t,
            'account_num': 'A2',
            'merchant_num': 'B2',
            'value': uniform(1,10)
        }
    ])

    rejected_records = wr.timestream.write(
        df,
        database=os.environ['database'],
        table=os.environ['table'],
        time_col='time',
        measure_col=['value'], 
        dimensions_cols=['account_num', 'merchant_num'],
        measure_name='transaction',
    )
    result = len(rejected_records)
    return result

def lambda_handler(event, context):
    print('----event---')
    print(event)
    print('----context---')
    print(context)
    response = {
        "Status": "None",
        "PhysicalResourceId": context.log_stream_name,
        "StackId": event['StackId'],
        "RequestId": event['RequestId'],
        "LogicalResourceId": event['LogicalResourceId'],
        "Data": {}
    }
    if event.get('RequestType') == 'Create':
        rejected = write_record()
        message = f"rejected {rejected}"        
        responseData = {}
        responseData['message'] = message
        logging.info('Sending %s to cloudformation', responseData['message'])
        cfnresponse.send(event, context, cfnresponse.SUCCESS, responseData)
        
    elif event.get('RequestType') == 'Delete':
        responseData = {}
        responseData['message'] = "Goodbye from lambda"
        logging.info('Sending %s to cloudformation', responseData['message'])
        cfnresponse.send(event, context, cfnresponse.SUCCESS, responseData)
    else:
        logging.error('Unknown operation: %s', event.get('RequestType'))