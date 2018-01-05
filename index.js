var WebSocketServer = require('websocket').server;
var http = require('http');

var clients = [ ];
var iceCandidates = [ ];
var offer;

var server = http.createServer(function(request, response) {
  // process HTTP request. Since we're writing just WebSockets
  // server we don't have to implement anything.
});
server.listen(1337, function() { });

// create the server
wsServer = new WebSocketServer({
  httpServer: server
});

// WebSocket server
wsServer.on('request', function(request) {
  var connection = request.accept(null, request.origin);
  var index = clients.push(connection) - 1;
  console.log('Index: ' + index)
  var iceCandidatesConnection = [ ];
  iceCandidates.push(iceCandidatesConnection)
  // Create iceCandidates array that should match the client it handles.

  // This is the most important callback for us, we'll handle
  // all messages from users here.
  connection.on('message', function(message) {
    var json = JSON.stringify(message, null, 4);
    var type = JSON.parse(message['utf8Data'])['type'];
    console.log(type);
    console.log(clients.length)
    if ((type == 'OFFER') && (clients.length == 2)) {
        console.log(clients.length)
        clients[1].send(JSON.stringify(message))
        console.log('OFFER!')
    } else if (type == 'ANSWER') {
        clients[0].send(JSON.stringify(message))
        console.log('ANSWER!')
    } else if (type != 'OFFER'){
        iceCandidatesConnection.push(message);
        /* for (var i=0; i<clients.length; i++) {
           clients[i].send(JSON.stringify(message));
        }*/
    }
    if ((type == 'OFFER') && (clients.length < 2)) {
        console.log("offer saved")
        offer = message;
    }
    if (clients.length >= 2) {
        clients[1].send(JSON.stringify(offer))
        console.log('OFFER!')
    }

    for (var i=0; i<clients.length; i++) {
        if (clients[i] != connection) {
            for (var j=0; j<iceCandidatesConnection.length; j++) {
                clients[i].send(JSON.stringify(iceCandidatesConnection[j]));
            }
        }
    }
  });

  connection.on('close', function(connection) {
    console.log('Close: ' + connection)
     clients = [ ]
  });
});