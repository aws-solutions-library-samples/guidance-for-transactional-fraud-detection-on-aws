from __future__ import print_function
import json
import base64
import os
from gremlin_python import statics
from gremlin_python.process.anonymous_traversal import traversal
from gremlin_python.process.graph_traversal import __
from gremlin_python.process.strategies import *
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from gremlin_python.process.traversal import T
from gremlin_python.process.traversal import Order
from gremlin_python.process.traversal import Cardinality
from gremlin_python.process.traversal import Column
from gremlin_python.process.traversal import Direction
from gremlin_python.process.traversal import Operator
from gremlin_python.process.traversal import P
from gremlin_python.process.traversal import TextP
from gremlin_python.process.traversal import Pop
from gremlin_python.process.traversal import Scope
from gremlin_python.process.traversal import Barrier
from gremlin_python.process.traversal import Bindings
from gremlin_python.process.traversal import WithOptions
from gremlin_python.structure.graph import Graph
from_ = Direction.OUT

neptuneclusterendpoint = os.environ.get('NeptuneClusterEndpoint')
neptuneclusterport = os.environ.get('NeptuneClusterPort')


def writeToNeptune(data):

    # Sample message payload received
    #  {
    #     "object_type": "transaction",
    #     "account": account["accountid"],
    #     "accountemail": account["email"] ,
    #     "phone": account["phone"],
    #     "merchant": merchant["merchantaccountid"],
    #     "transaction_amount": str(transaction_amount),
    #     "time": str_now
    # }

    graph = Graph()
    connection = f'wss://{neptuneclusterendpoint}:{neptuneclusterport}/gremlin'
    remoteConn = DriverRemoteConnection(connection, 'g')

    g = graph.traversal().withRemote(remoteConn)

    result = g.V(data['account']).fold().coalesce(
        __.unfold(),
        __.addV('account').property(T.id, data['account'])
    ).V(data['merchant']).fold().coalesce(
        __.unfold(),
        __.addV('merchant').property(T.id, data['merchant'])
    ).V(data['accountemail']).fold().coalesce(
        __.unfold(),
        __.addV('email').property(T.id, data['accountemail']).addE(
            "hasemail").from_(__.V(data['account']))
    ).V(data['accountemail']). in_("hasemail").hasId(data['account']).fold().coalesce(
        __.unfold(),
        __.addE("hasemail").from_(__.V(data['account'])).to(
            __.V(data['accountemail']))
    ).V(data['phone']).fold().coalesce(
        __.unfold(),
        __.addV('phone').property(T.id, data['phone']).addE(
            "hasphone").from_(__.V(data['account']))
    ).V(data['phone']). in_("hasphone").hasId(data['account']).fold().coalesce(
        __.unfold(),
        __.addE("hasphone").from_(
            __.V(data['account'])).to(__.V(data['phone']))
    ).addV("transaction").as_("t").property("amount", data["transaction_amount"]
                                            ).property("timestamp", data["time"]
                                                       ).addE("linkedtransaction"
                                                              ).from_(__.V(data['account'])).to(__.select("t")
                                                                                                ).addE("linkedtransaction").from_(__.V(data['merchant'])).to(__.select("t")).iterate()

    print(result)
    remoteConn.close()


def lambda_handler(event, context):
    for record in event['Records']:
        # Kinesis data is base64 encoded so decode here
        payload = base64.b64decode(record["kinesis"]["data"])
        print("Decoded payload: " + str(payload))
        writeToNeptune(json.loads(payload))
