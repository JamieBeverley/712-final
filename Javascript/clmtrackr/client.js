try{
var	ws = new WebSocket("ws://"+location.hostname+":"+location.port, 'echo-protocol');
} catch (e){
	console.log("no WebSocket connection")
}

var ratings = []
var inStandby=false;
var waitTime=15000; // time bot takes to judge approval
var ratingsPerPeriod = 80; // number of 'samples' of happiness taken in that time


function sendRating (){
	// If enough ratings were read in the past waitTime, then send the rating
	// otherwise send a 'noRating' message
	if(ratings.length>(0.1*ratingsPerPeriod)){
		var rating = mean(ratings);	
		console.log("ratings sampled:  " +ratings)
		console.log("mean of said ratings: "+rating)
		var msg = {
			type:"rate",
			value:rating
		}	
		try {
			console.log("sending a rating of:  "+rating)
			ws.send(JSON.stringify(msg))
		} catch (e){
			console.log(e)
			//@ some more tricky error handling - communicate to server (which is waiting)
			// that something got messed up, re-listen or attempt resend...
		}
	} else {
		//@ say something
		console.log("No ratings made, not enough correspondance...")
		var msg = {
			type:"noRating"
		}	
		try {
			ws.send(JSON.stringify(msg))
		} catch (e){console.log(e)}
	}
	ratings = [];
}

function showVideo(){
	var e = document.getElementById("checkbox").checked
	var container=document.getElementById("container")
	console.log(e)
	if(e){
		container.hidden = false;
	} else {
		container.hidden = true;
	}

}

function getRating(){
	var params = ctrack.getCurrentParameters();
	var emotions = ec.meanPredict(params)
	var x = document.getElementById("ratingIndicator");
	// Only if the faceTracker is working/has registered a face
	if(ctrack.getCurrentPosition()){
		ratings.push(emotions[3].value);
		if(inStandby==false){
			x.innerHTML = (Math.round(emotions[3].value*100)/10)+" / 10";
		}
	} else{
		if(inStandby==false){
		x.innerHTML = "I can't tell how you feel about this one...";
	}
	}
	
}

// Message parsing from server
ws.addEventListener('message', function(message){
	var msg = JSON.parse(message.data)

	if (msg.type == "getRating"){
		console.log('getting a rating...')	
		var getRatingInterval = setInterval(getRating,waitTime/ratingsPerPeriod);
	
		// After 'waitTime' - kill the 'getRating' timeout and send the rating to server.
		setTimeout(sendRating,waitTime);
		setTimeout(function(){
			clearInterval(getRatingInterval)
			document.getElementById("ratingIndicator").innerHTML="";
		}, waitTime)
	} else if(msg.type =="standby"){
		console.log("standby");
		standby()
		
	} else if(msg.type == "say"){
		say(msg.value);
	} else if (msg.type == "sayMelody"){
		sayMelody(msg.value);
	}else {
		console.log("##### Warning - unidentified message from server: "+msg.type)
	}

})

function say(s){
	var textArea = document.getElementById("say");
	textArea.innerHTML = s;
}

function sayMelody(s){
	var textArea = document.getElementById("sayMelody");
	textArea.innerHTML = s;
}

function standby(){
	inStandby=true;
	document.getElementById("ratingIndicator").innerHTML="";
	ratings =[];
	var standbyInterval = setInterval(getRating,waitTime/ratingsPerPeriod)
	console.log("entered standby")
	setTimeout(function(){
		clearInterval(standbyInterval);
		// If only got rating for less than 30% of samples
		if(ratings.length/ratingsPerPeriod < 0.3){
			// var m = {type:'keepStandby'}
			// ws.send(JSON.stringify(m))
			console.log("staying in standby, only recognized: "+(ratingsPerPeriod.length/ratingsPerPeriod)+"%")
			standby()
		} else {
			ratings = []
			var m = {type:'awaken'}
			ws.send(JSON.stringify(m))
			console.log("exited standby")
		}
	},waitTime)
}

var vid = document.getElementById('videoel');
var vid_width = vid.width;
var vid_height = vid.height;
var overlay = document.getElementById('overlay');
var overlayCC = overlay.getContext('2d');

/********** check and set up video/webcam **********/

function enablestart() {
	var startbutton = document.getElementById('startButton');
	startbutton.value = "start";
	startbutton.disabled = null;
}

function adjustVideoProportions() {
	// resize overlay and video if proportions are different
	// keep same height, just change width
	var proportion = vid.videoWidth/vid.videoHeight;
	vid_width = Math.round(vid_height * proportion);
	vid.width = vid_width;
	overlay.width = vid_width;
}

function gumSuccess( stream ) {
	// add camera stream if getUserMedia succeeded
	if ("srcObject" in vid) {
		vid.srcObject = stream;
	} else {
		vid.src = (window.URL && window.URL.createObjectURL(stream));
	}
	vid.onloadedmetadata = function() {
		adjustVideoProportions();
		vid.play();
	}
	vid.onresize = function() {
		adjustVideoProportions();
		if (trackingStarted) {
			ctrack.stop();
			ctrack.reset();
			ctrack.start(vid);
		}
	}
}

function gumFail() {
	alert("There was some problem trying to fetch video from your webcam. If you have a webcam, please make sure to accept when the browser asks for access to your webcam.");
}

navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia;
window.URL = window.URL || window.webkitURL || window.msURL || window.mozURL;

// check for camerasupport
if (navigator.mediaDevices) {
	navigator.mediaDevices.getUserMedia({video : true}).then(gumSuccess).catch(gumFail);
} else if (navigator.getUserMedia) {
	navigator.getUserMedia({video : true}, gumSuccess, gumFail);
} else {
	alert("This demo depends on getUserMedia, which your browser does not seem to support. :(");
}

vid.addEventListener('canplay', enablestart, false);

/*********** setup of emotion detection *************/

// set eigenvector 9 and 11 to not be regularized. This is to better detect motion of the eyebrows
pModel.shapeModel.nonRegularizedVectors.push(9);
pModel.shapeModel.nonRegularizedVectors.push(11);

var ctrack = new clm.tracker({useWebGL : true});
ctrack.init(pModel);
var trackingStarted = false;

function startVideo() {
	// start video
	vid.play();
	// start tracking
	ctrack.start(vid);
	trackingStarted = true;
	document.getElementById("controls").remove();
	document.getElementById("sayMelodyContainer").hidden=false;
	// start loop to draw face
	drawLoop();
}

var accuracyFactor = 0.5

//probably called every frame(?)
function drawLoop() {
	requestAnimFrame(drawLoop);
	overlayCC.clearRect(0, 0, vid_width, vid_height);
	//psrElement.innerHTML = "score :" + ctrack.getScore().toFixed(4);
	if (ctrack.getCurrentPosition()) {
		ctrack.draw(overlay);
	}
	var cp = ctrack.getCurrentParameters();

	var er = ec.meanPredict(cp);
	// console.log(er[3]);
	if (er) {
		updateData(er); // draws svg bars
		for (var i = 0;i < er.length;i++) {
			if (er[i].value > accuracyFactor) {
				document.getElementById('icon'+(i+1)).style.visibility = 'visible';
			} else {
				document.getElementById('icon'+(i+1)).style.visibility = 'hidden';
			}
		}
	}
}


delete emotionModel['disgusted'];
delete emotionModel['fear'];
var ec = new emotionClassifier();
ec.init(emotionModel);
var emotionData = ec.getBlank();




/************ d3 code for barchart *****************/
// setInterval(function (){console.log(ec.getEmotions())},500)


var margin = {top : 20, right : 20, bottom : 10, left : 40},
	width = 400 - margin.left - margin.right,
	height = 100 - margin.top - margin.bottom;

var barWidth = 30;

var formatPercent = d3.format(".0%");

var x = d3.scale.linear()
	.domain([0, ec.getEmotions().length]).range([margin.left, width+margin.left]);

var y = d3.scale.linear()
	.domain([0,1]).range([0, height]);

var svg = d3.select("#emotion_chart").append("svg")
	.attr("width", width + margin.left + margin.right)
	.attr("height", height + margin.top + margin.bottom)

svg.selectAll("rect").
	data(emotionData).
	enter().
	append("svg:rect").
	attr("x", function(datum, index) { return x(index); }).
	attr("y", function(datum) { return height - y(datum.value); }).
	attr("height", function(datum) { return y(datum.value); }).
	attr("width", barWidth).
	attr("fill", "#2d578b");

svg.selectAll("text.labels").
	data(emotionData).
	enter().
	append("svg:text").
	attr("x", function(datum, index) { return x(index) + barWidth; }).
	attr("y", function(datum) { return height - y(datum.value); }).
	attr("dx", -barWidth/2).
	attr("dy", "1.2em").
	attr("text-anchor", "middle").
	text(function(datum) { return datum.value;}).
	attr("fill", "white").
	attr("class", "labels");

svg.selectAll("text.yAxis").
	data(emotionData).
	enter().append("svg:text").
	attr("x", function(datum, index) { return x(index) + barWidth; }).
	attr("y", height).
	attr("dx", -barWidth/2).
	attr("text-anchor", "middle").
	attr("style", "font-size: 12").
	text(function(datum) { console.log(datum.emotion);return datum.emotion;}).  // the text under the svg bar
	attr("transform", "translate(0, 18)").
	attr("class", "yAxis");

function updateData(data) {
	// update
	var rects = svg.selectAll("rect")
		.data(data)
		.attr("y", function(datum) { return height - y(datum.value); })
		.attr("height", function(datum) { return y(datum.value); });
	var texts = svg.selectAll("text.labels")
		.data(data)
		.attr("y", function(datum) { return height - y(datum.value); })
		.text(function(datum) { return datum.value.toFixed(1);});

	// enter
	rects.enter().append("svg:rect");
	texts.enter().append("svg:text");

	// exit
	rects.exit().remove();
	texts.exit().remove();
}

/******** stats ********/

stats = new Stats();
stats.domElement.style.position = 'absolute';
stats.domElement.style.top = '0px';
document.getElementById('container').appendChild( stats.domElement );

// update stats on every iteration
document.addEventListener('clmtrackrIteration', function(event) {
	stats.update();
}, false);



////////////////////////////////////////////////////////////////////////
 
// function sendRating(a){
// 	var msg = {
// 		type:"rate",
// 		value:a
// 	}	
// 	try{
// 		ws.send(JSON.stringify(msg))
// 	} catch (e){
// 		console.log(e)
// 	}

// }

// Uhg
function mean(l){
	var a=0;
	for(var i =0; i<l.length;i++){
		a = a+l[i];
	}
	return (a/l.length)
}










///How standby works:
// client registers little action when trying to determine emotion - sends noRating message to server (which goes to SC)
// SC goes into free improv mode from noRateMessage
// SC tells server to go into standby which is broadcasted to client(s)
// Clients recursively calls callback function until it at least some percentage of readings
// client messages server with 'awaken' which is passed to SC
// SC exits free improv, 'step' resumes, polling client for 'getRating' messages.