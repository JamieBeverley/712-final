
// var fs = require('fs');
// // var http = require('http');
// var https = require('https');
// var privateKey  = fs.readFile('privatekey.pem');
// var certificate = fs.readFile('certificate.pem');

// var credentials = {key: privateKey, cert: certificate};
// var express = require('express');
// var expressServer = express();

// // your express configuration here

// // var httpServer = http.createServer(app);
// var server = https.createServer(credentials, expressServer);

// // httpServer.listen(8080);
// // httpsServer.listen(8443,function(){console.log("listening")});

////////////////////////////////////////////////////////////////////////////
// Setup the http server
var http = require('http');
var express = require('express');
var server = http.createServer();
var expressServer = express();

// uses current directory
expressServer.use(express.static(__dirname));
server.on('request', expressServer)


//http server listening on 8000
server.listen(8000, function(){console.log("listening")})

// from this can make websocket server
var WebSocket = require('ws')
var wsServer = new WebSocket.Server({server: server});



//////////////////////////////////////////////////////////////////////////
// Setup osc message handling
var osc = require ('osc')


var scOSC = new osc.UDPPort({
	localAddress: "0.0.0.0", 
	localPort: 9000,
	remoteAddress: "127.0.0.1",
	remotePort: 9001
})
scOSC.open();

// Handle sc messages
scOSC.on('message',function(oscMsg){
	
	if (oscMsg.address =="/getRating"){
		broadcast({type:'getRating'})
	} else if(oscMsg.address == "/standby"){
		console.log('entering standby mode')
		broadcast({type:"standby"})

	} else if (oscMsg.address == "/say"){
		broadcast({type:"say",value:oscMsg.args[0]})
	} else{
		console.log("######## WARNING - Received osc from SC with no matching address")
	}
})




var id=0;
var clients = {};
var close = {}
var numClients=0;

wsServer.on('connection', function(r){
	id++;
	console.log('new client')
	r.id =id;
	clients[id] = {
		id: id,
		client: r,
		rating: 0.5
	}

	r.on('message',function(message){
		var msg = JSON.parse(message)

		if (msg.type =="rate"){
			clients[id].rating = msg.value; 
			console.log("rating received with value: "+msg.value)

			var m = {
				address:"/rate",
				args:[msg.value]
			}
			scOSC.send(m);
		} else if (msg.type == "noRating"){
			console.log("No rating received from client")
			var m = {
				address:"/noRating",
				args:[]
			}
			scOSC.send(m)
		} else if (msg.type == "awaken"){
			console.log("received awaken message from client")
			var m = {
				address:"/awaken",
				args:[]
			}
			scOSC.send(m)
		} else {
			console.log("####### Warning unmatched message received from client:  " +msg)
		}
	});
	r.on('close',function(reasonCode,description){
		// for(i in clients){
		// 	if (clients[i].id==r.id){
		// 		delete clients[i];
		// 		break;
		// 	}
		// }
		delete clients[r.id]
		console.log("client left, now client list is :"+clients)
	})


})


function broadcast (m){
		for(i in clients){
			console.log(clients[i] +"     "+i)
			try{
			clients[i].client.send(JSON.stringify(m))
			} catch (e){console.log("Error sending 'getRating' message to clients: "+e)}	
		}
}





