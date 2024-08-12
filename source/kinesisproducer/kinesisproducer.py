import random
import string
import argparse
import json
import logging
import datetime
import json
import datetime
import boto3
import logging
import sys

import multiprocessing as mp

from multiprocessing import Process
from botocore.config import Config

from threading import Thread
from queue import Queue

log_format = '%(asctime)s - %(name)s - %(processName)s - %(threadName)s - %(levelname)s - %(message)s'
logging.basicConfig(format=log_format, level=logging.INFO)
logger = logging.getLogger("kinesis-producer")

# create accounts 
# few of accounts from the given accounts will show fraud behaviour
def create_accounts_merchants():
    counter = 0
    accounts = []
    while counter < 100:
        
        account = {
            "email": f"email-{random.randint(1,99)}@ntdemo.com",
            "phone": f"123456{random.randint(1,99)}",
            "accountid": f"1234-5678-{counter}"
        }

        if counter >= 20 and counter < 31:
            account["email"] =  "shared@ntdemo.com"
            account["phone"] =  "10001000"
        
        accounts.append(account)
        counter = counter + 1
        print(account) 

    counter = 1000
    merchants = []
    while counter < 1020:
        merchant = {
            "email": f"email-{random.randint(1000,1020)}@ntdemo.com",
            "phone": f"123456{random.randint(1000,1020)}",
            "merchantaccountid": f"1234-5678-{counter}"
        }

        merchants.append(merchant)
        counter = counter + 1
        print(merchant)

    return accounts, merchants

def getOptions(args=sys.argv[1:]):
    parser = argparse.ArgumentParser(
        description="Sample kinesis producer")
    parser.add_argument("-r", "--awsregion", type=str,
                        required=True, help="AWS region")
    parser.add_argument("-k", "--kinesisstream", type=str, required=True,
                        help="Identifier of kinesis stream")
    options = parser.parse_args(args)
    return options

def pick_fraud_accounts_merchants(accounts,merchants):
    # TODO:
    # pick 4 account from accounts which is shared by atleast more than one phone numbers or email addresses

    counter = 0;
    fraudaccounts = []
    fraudmerchants = []

    while counter < 5:
        fraudaccounts.append(accounts[random.randint(20,30)])
        fraudmerchants.append(merchants[random.randint(2,4)])   
        counter = counter + 1
    
    print(fraudaccounts)
    print(fraudmerchants)

    return  fraudaccounts,fraudmerchants


def get_data(account, merchant,transaction_amount):
    #  Pull out internal metadata and flatten

    now = datetime.datetime.utcnow()
    str_now = now.strftime("%b %d, %Y, %H:%M:%S.{:03d}".format(now.microsecond // 1000))

    data = {
        "object_type": "transaction",
        "account": account["accountid"],
        "accountemail": account["email"] ,
        "phone": account["phone"],  
        "merchant": merchant["merchantaccountid"],
        "transaction_amount": str(transaction_amount),
        "time": str_now
    }
     
    return data
     
def get_transaction_data(accounts,merchants):
    # For usecase 1: High Valued Transaction in said period of time
    # 1. Select which set of accounts are going to show this pattern. The current data generation process is too random and lot of data points are generated
    # 2. One these accounts show up in the TimeStream Aggregate tables, we need to analyse them in Neptune 
    # 3. Pull out the account ids from the the aggregate table using python code or manually
    # 4. Investigate network for the user. in the network we need to investigate is the user associated with the accounts involved in these transcations share accounts. or are connected to other accounts
    # via some meta data like emailid, phone, epaymentidentifier, address, ip address
    

    transaction_amount = random.choice([100, 200, 500])
    account = accounts[random.randint(0,98)]
    merchant = merchants[random.randint(0,18)]

    return get_data(account, merchant,transaction_amount);

def get_transaction_data_fraud(fraud_accounts,fraud_merchants):
    # For usecase 1: High Valued Transaction in said period of time
    # 1. Select which set of accounts are going to show this pattern. The current data generation process is too random and lot of data points are generated
    # 2. One these accounts show up in the TimeStream Aggregate tables, we need to analyse them in Neptune 
    # 3. Pull out the account ids from the the aggregate table using python code or manually
    # 4. Investigate network for the user. in the network we need to investigate is the user associated with the accounts involved in these transcations share accounts. or are connected to other accounts
    # via some meta data like emailid, phone, epaymentidentifier, address, ip address
    now = datetime.datetime.utcnow()
    str_now = now.strftime("%b %d, %Y, %H:%M:%S.{:03d}".format(now.microsecond // 1000))

    transaction_amount = random.choice([10000, 20000,50000,10000])
    account = fraud_accounts[random.randint(0,4)]
    merchant = fraud_merchants[random.randint(0,3)]

    return get_data(account, merchant,transaction_amount);


def kickoff_producer(q, awsregion, kinesisdatastream, accounts, merchants, fraud_accounts, fraud_merchants, isFraud):
    print(kinesisdatastream)
    my_config = Config(
        region_name=awsregion,
        signature_version='v4',
        retries={
            'max_attempts': 10,
            'mode': 'standard'
        }
    )

    kinesis_client = boto3.client('kinesis', config=my_config)

    # Invoke data generation code to create profles and pick fraud accounts from the account
    # from the fraud user accounts using Timestream
    # fraud accounts>> user >> user=metadata << user << link accounts  to find possible fraud ring, using Neptune

    loopCounter = 0

    while loopCounter < 500:
        print(isFraud)
        if isFraud == False:
            data = get_transaction_data(accounts, merchants)
        else:
            if loopCounter < 50:
                data = get_transaction_data_fraud(fraud_accounts, fraud_merchants)
            else:
                print("no data to push")
                return
        
        loopCounter  = loopCounter + 1

        print(data,flush=True)
        kinesis_client.put_record(
            StreamName=kinesisdatastream,
            Data=json.dumps(data),
            PartitionKey="producerpartition")  


if __name__ == '__main__':
    options = getOptions(sys.argv[1:])
    logger.info(f"arguments supplied: {options.awsregion} {options.kinesisstream} ")

    accounts, merchants = create_accounts_merchants()
    fraud_accounts, fraud_merchants = pick_fraud_accounts_merchants(accounts=accounts, merchants=merchants)

    q = Queue()
    p = Thread(target=kickoff_producer, args=(q,options.awsregion, options.kinesisstream,accounts, merchants, fraud_accounts, fraud_merchants, False))
    p.start()

    q1 = Queue() 
    p1 = Thread(target=kickoff_producer, args=(q,options.awsregion, options.kinesisstream,accounts, merchants, fraud_accounts, fraud_merchants, True))
    p1.start()



