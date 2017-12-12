~node = NetAddr("127.0.0.1", 9000);



~node.sendMsg("/getRating");


(
OSCdef(\rate,{
	|msg|
	msg.postln;
	~answer = msg[1];
	~c.test = true;
	~c.signal;
},"/rate",recvPort:9001);

OSCdef(\noRating,{
	|msg|
	"noRating received".postln;
	~answer = nil;
	~c.test=true;
	~c.signal;
	~g.node.sendMsg("/standby");
	~g.startStandby();
	"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! start ~g's generative music".postln;

},"/noRating",recvPort:9001);

OSCdef(\awaken,{
	|msg|
	"awaken received".postln;
	~g.inStandby = false;
	~g.standbyCondition.test=true;
	~g.standbyCondition.signal;
	"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ stop ~g's generative music".postln;

	//... resume

},"/awaken",recvPort:9001);
)