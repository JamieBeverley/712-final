
(
~outBus = Bus.audio(Server.default,2).index;

SynthDef(\bell,
	{
		arg amp= -20.dbamp,dur,curve= -6, freq = 800,modPartial=16,pan=0,sustain;
		var audio = SinOsc.ar(freq+SinOsc.ar(freq:modPartial*freq/16,mul:modPartial*freq/16),mul:amp);
		// var env = EnvGen.ar(Env.adsr(attackTime:0.01,decayTime:0.5,sustainLevel:0.2,releaseTime:1,curve:curve),gate:gate,doneAction:2);
		var env = EnvGen.ar(Env.perc(attackTime:0.01,releaseTime:max(1,sustain),curve:curve),doneAction:2);
		audio = audio*env;
		Out.ar(~outBus, Pan2.ar(audio,pan));
	}
).add;

(
SynthDef(\out,{
	|lpf=24000, hpf=10, reverb=0.4,db=0|
	var audio = In.ar(~outBus,2)*(db.dbamp);
	audio = FreeVerb.ar(audio,mix:reverb.clip(0,1),room:0.9,damp:0.9);
	Out.ar(0,audio);
}).add;

);

)




// Start//////////////////////////////////////////////////
(
var history = Dictionary.new();
var fitnessFunc;
var breedFunc;
Synth.new(\out);
~scale = 48+[0,3,5,7,8,10,12,15,17,19,20,22,24];
~c=Condition.new;
fitnessFunc= {|a|
	var audienceFitnessVal;
	var aiFitnessVal;
	"----Playing:".postln;
	("     "+a).postln;
	"How was it? (store answer in ~answer)".postln;
	~g.say(["How do you feel about this one?","How's this melody?","What about this melody?"].choose);
	~g.node.sendMsg("/getRating");
	~c.hang;
	("You thought it was: "+~answer).postln;
	~answer;
};


breedFunc= {
	|a, b|
	var dominant;
	var recessive;
	var notes;
	var durations;
	var instruments;

	// pick dominant/recessive parent randomly weighted by their respective fitnesses
	dominant = [a,b].wchoose([a.fitness,b.fitness].normalizeSum);
	if(dominant==b,{
		recessive = a;
	},{
		recessive = b;

	});




	notes = dominant.value.notes.collect({|v,i| [v,recessive.value.notes[i%(recessive.value.notes.size)]].wchoose([dominant.fitness,recessive.fitness].normalizeSum)});

	durations = dominant.value.durations.collect({
		|v,i|
		[v,recessive.value.durations[i%recessive.value.durations.size]].wchoose([dominant.fitness,recessive.fitness].normalizeSum)
	});


	instruments = dominant.value.instruments.collect({|v,i| [v,recessive.value.instruments[i%recessive.value.instruments.size]].wchoose([dominant.fitness,recessive.fitness].normalizeSum)});

	Melody(notes,durations,instruments);
};



~pop=List();
6.do{
	var s=List.new;
	s.add(~scale.choose);
	8.do{
		if(2.rand==1,{s.add(~scale.choose)});
	};

	~pop.add(Individual(10.rand/10,value:Melody(s,[0.5,0.125,0.25,1,2].select({2.rand==1}).scramble,[\bell])));
};
~g = Genetic(replacementSize:3,population:~pop,fitnessF:fitnessFunc,mutateFunction:{},breedFunc:breedFunc,scale:~scale,maxMelodyLength:8);

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
	"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ stop ~g's generative music".postln;

	~g.inStandby = false;
	~g.standbyCondition.test=true;
	~g.standbyCondition.signal;

	//... resume

},"/awaken",recvPort:9001);
);

~g.run();

)// end /////////////////////////////////////


(
~answer=0.9;
~c.test=true;
~c.signal;
)
