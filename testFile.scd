Server.default.options.sampleRate=48000
var audio = SinOsc.ar(freq+SinOsc.ar(freq:modPartial*freq/128,mul:modPartial*freq/16),mul:amp);
// ^ add some reverb too.

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
Synth.new(\out);
);

)




// Start//////////////////////////////////////////////////
(
var history = Dictionary.new();
var fitnessFunc;
var breedFunc;
~scale = 48+[0,3,5,7,8,10,12,15,17,19,20,22,24];
~c=Condition.new;
fitnessFunc= {|a|
	var audienceFitnessVal;
	var aiFitnessVal;
	"----Playing:".postln;
	("     "+a).postln;
	// a.play();

	"How was it? (store answer in ~answer)".postln;
	~c.hang;
	~answer.postln;
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
	/*	"------MATED: ".postln;
	("     "+a).postln;
	("     "+b).postln;
	"------and made: ".postln;
	("     "+mel).postln;*/
	Melody(notes,durations,instruments);
};

// Need to run in a routine so that Condition can halt it
Routine {
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

	inf.do{
		// var = Genetic.wChooseN(~g.population,1)[0];
		// var play = ~g.getNext();
		// var index = ~g.population.collect({|v,i| v==play}).select({|a|a})[0];

		// play.value.play;
		// ~g.fitnessFunc.value(play.value);
		"pre step".postln;
		~g.step();
		"post step".postln;
		~g.evolve();
		"~g's population:   ".postln;
		"______________________________".postln;
		~g.population.do{
			|i|
			"        ".post;
			i.postln;
		};
		"______________________________".postln;
	}
}.play
)// end /////////////////////////////////////


(
Routine(
	{
		100.do{
			~answer=110.rand/110;
			~c.test=true;
			~c.signal;
			0.01.wait;
		}
}).play;
)
~g.generations

(
~answer=0.9;
~c.test=true;
~c.signal;
)

~g.showPopulation