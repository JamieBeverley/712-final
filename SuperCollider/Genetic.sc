Genetic {
	//max population size implicit in the initial pop.
	var <>replacementSize; // # of new individuals created by breed, cross, and mutation, and # of least fit removed from prior gen.
	var <>population;  // should be a list of Individuals (from Individual.sc) - a fitness and value pair
	var <>fitnessFunc; // assigns a fitness rating for each individual in a population (from 0 to 1)
	var <>mutateFunc; // maybe this can be part of the below 'breedFunc'
	var <>breedFunc; // a func taking: [[fitness (0-1), individual]], returning a population ([individual])
	var <>lastSelected;
	var <>memory;    // a dictionary of all previous played melodies, where the key is the string representation of the notes (using durations too would create too many 'memories' - the algorithm would have to run very long to ever recall anything specific about it's memeory.
	var <>history;   // a List of the past 20 individuals played. Useful for seeing if something has been played recently
	var <>scale;
	var <>maxMelodyLength; //length in the 'notes' list
	var <>minMelodyLength; //length in the 'notes' list
	var <>generations; //just keeping track of how many times 'step' has been called (how many generations)
	var <>node;
	var <>inStandby;
	var <>standbyCondition;

	*new{
		|replacementSize, population, fitnessF, mutateFunction,breedFunc,scale,maxMelodyLength,minMelodyLength=1|


		if (fitnessF==nil,{Error("Genetic must be supplied a fitness function")});
		if (population==nil,{population = Melody.new!population.size});
		if (replacementSize==nil,{replacementSize = 1});

		if (replacementSize>=(population.size-1),{Error("replacement size must be less than the population size -1 so 2 still exist each generation to breed").throw});
		if (breedFunc==nil,{breedFunc=Genetic.breed});
		^super.new.init(replacementSize, population, fitnessF, mutateFunction,breedFunc,scale,maxMelodyLength,minMelodyLength);

	}


	init{
		|replacementSize,population, fitnessF, mutateFunction, breedFunc,scale,maxMelodyLength,minMelodyLength=1|

		this.replacementSize= replacementSize;
		this.population = population;
		this.fitnessFunc = fitnessF;
		this.mutateFunc = mutateFunction ;
		this.breedFunc = breedFunc;
		this.lastSelected = nil;
		this.memory = Dictionary.new();
		this.history = List.new;
		this.population.do({|i|
			this.memory.add(i.hash->Memory.new(i.value,i.fitness));
		});
		this.scale = scale;
		this.maxMelodyLength = maxMelodyLength;
		this.minMelodyLength = minMelodyLength;
		this.generations = 0;
		this.node = NetAddr("127.0.0.1", 9000);
		this.inStandby = false;
		this.standbyCondition = Condition.new(test:true);
		^this;
	}


	// takes [[fitness,individual]] -> replacementSize -> [[fitness, individual]](sanse Replacementsize # of indiv)
	*wChooseN{
		|population, n|
		var weights = population.collect({|v,i| v.fitness;});
		var indices = population.collect({|v,i|i;});
		var answer = List.new;
		if((n>population.size),{"wchooseN - n should not be greater than size".warn});

		while ( {(n>answer.size)} ,{
			var i = indices.wchoose(weights.normalizeSum);
			answer.add(population[i]);
			weights[i]=0;
		});
		^answer;
	}


	// Removes 'this.replacementSize' number of individuals, and breed
	evolve{
		// Remove 'replacement size' # of individuals from population
		this.population = Genetic.wChooseN(this.population,this.population.size-this.replacementSize);
		this.breed();
	}

	// breed:: [[fitness, individual]] -> [individual] (ie. a population)
	breed{
		var children = List.new(size:this.replacementSize);

		this.replacementSize.do{
			var parents = Genetic.wChooseN(this.population, 2);
			// Birthing a child with the breedFunc, giving average fitness of parents,
			var child = Individual((parents[0].fitness+parents.[1].fitness)/2,this.breedFunc.value(parents[0],parents[1]));

			// Mutate:
			child = this.mutate(parents[0],parents[1],child);

			// Add child to list that re-populates population, and to memory dictionary
			children.add(child);

			// if the child has already been played at some point, add the fitness to it's memory
			// otherwise create a new entry and add it into the memroy dictionary.
			if( (this.memory.at(child.hash)==nil),{
				this.memory.add(child.hash->Memory.new(value:child.value,fitness:child.fitness));
			},{
				this.memory.at(child.hash).add(fitness:child.fitness)
			});

		};


		this.population = (this.population++children);

	}

	mutate{
		|mother, father, child|
		var mutateFactor=0;
		var durationMutateFactor=0;
		var variances = List.new;
		var variancesLength = mother.value.notes.size;
		var lengthFactor; //for lengthening/shortening the melody
		var durLengthFactor;



		// Sort of detecting for 'inbreeding' in notes - when mother and father are very similar, more mutation
		min(mother.value.notes.size,father.value.notes.size).do{
			|i|
			variances.add((mother.value.notes[i]-father.value.notes[i]).abs);
		};
		variances.do{
			|i|
			mutateFactor = mutateFactor+(i/((this.scale.maxItem)-(this.scale.minItem)));
		};
		mutateFactor = 1 - (mutateFactor/variances.size);

		variances = List.new;
		// Detecting for 'inbreeding' in durations - when mother and father have very similar durations, more mutation
		min(mother.value.durations.size,father.value.durations.size).do{
			|i|
			variances.add((mother.value.durations[i]-father.value.durations[i]).abs);
		};

		//@ should probably make instance variables for max duration and min duration, it's hard coded as 0.125, and 2 here...
		variances.do{
			|i|
			durationMutateFactor = durationMutateFactor + (i/(2-0.125));
		};
		durationMutateFactor = 1-(durationMutateFactor/variances.size);


		// Exponential - things that are just slightly 'inbred' aren't mutated too often, more inbreeding with super similar ones
		mutateFactor = mutateFactor**4;

		/*		("### Mother:  "+mother).postln;
		("### Father:  "+father).postln;
		("### Mutate Factor:   "+mutateFactor).postln;
		("### Child melody:   "+child.value.notes).postln;*/

		// mutating a note up or down a step in the scale according to the mutate factor - some complexity if the scale has changed
		child.value.notes.collectInPlace({
			|v,i|

			if((this.scale.indexOf(v)==nil),{
				var x=this.scale.choose;

				// find the closest lower note in the new scale to 'v'
				(this.scale.size-1).do{
					|i|
					if((this.scale[i]<v)&&(this.scale[i+1]>v),{
						x=this.scale[i];
					});
				};

				x;
			},{// This will be the case unless this.scale has changed
				this.scale[(this.scale.indexOf(v)+[-2,-1,0,1,2].wchoose([mutateFactor*0.2,mutateFactor*0.3,1-mutateFactor,mutateFactor*0.3,mutateFactor*0.2].normalizeSum))%this.scale.size];
			};);

		});


		// mutate durations ss
		child.value.durations.collectInPlace({
			|v,i|
			(v+[-0.25,-0.125,0,0.125,0.25].wchoose([durationMutateFactor/6,durationMutateFactor/3,1-durationMutateFactor,durationMutateFactor/3,durationMutateFactor/6].normalizeSum)).clip(0.125,2);
		});


		durLengthFactor = child.value.durations.size/this.maxMelodyLength;

		if(durLengthFactor <= 0.5,{
			/*			child.value.durations = child.value.durations.collect({|v,i| [v,[v,[]].wchoose([mutateFactor*durLengthFactor,1-
			(mutateFactor*durLengthFactor)].normalizeSum)]}).flatten.flatten;*/
			// ">>>>>>>>>>>>>>>>.>>>>>>>>>>>>>>>>>.>>>>>>>> going to grow the duration list".postln;
			child.value.durations = child.value.durations.collect({|v,i| [v,[v,[]].wchoose([mutateFactor,1-(mutateFactor)].normalizeSum)]}).flatten.flatten;
		},{
			child.value.durations = child.value.durations.collect({|v,i| [v,[]].wchoose([1-(mutateFactor*(1-durLengthFactor)),mutateFactor*(1-durLengthFactor)].normalizeSum)}).flatten;
		});

		// child.value.durations = child.value.durations.collect({
		// 	|v,i|
		// 	//mutate
		//
		// 	[[v,[]].wchoose([1-mutateFactor,mutateFactor])]++[[[],v,v+0.125,max(0.125,v-0.125)].wchoose([1-mutateFactor,mutateFactor/3,mutateFactor/3,mutateFactor/3].normalizeSum)];
		// }).flatten.flatten; //double flatten is necessary for: [1,[]] -> [1]



		lengthFactor = child.value.notes.size/this.maxMelodyLength;
		// if note length is converging towards the max of the list, mutate to remove some
		// if note lneght is converging towards the min of the list, mutate to add more

		if ((lengthFactor<=0.5),{
			child.value.notes = child.value.notes.collect({|v,i| [v,[v,[]].wchoose([mutateFactor*lengthFactor,1-(mutateFactor*lengthFactor)].normalizeSum)]}).flatten.flatten;
		},{
			child.value.notes = child.value.notes.collect({|v,i| [v,[]].wchoose([1-(mutateFactor*(1-lengthFactor)),mutateFactor*(1-lengthFactor)].normalizeSum)}).flatten;
		});


		// ("### Mutant melody notes:   "+child.value.notes).postln;


		^child;
	}


	run{
		Routine{
			inf.do{
				if(this.inStandby,{this.standbyCondition.hang});
				this.step();
				"post step".postln;
				this.evolve();
				"~g's population:   ".postln;
				"______________________________".postln;
				this.population.do{
					|i|
					"        ".post;
					i.postln;
				};
				"______________________________".postln;
			}
		}.play;
	}

	say{
		|a|
		this.node.sendMsg("/say",a);
	}

	//Expecting 'a' to be a melody
	sayMelody{
		|a|
		this.node.sendMsg("/sayMelody",a.showNotes());
	}

	startStandby{

		Pdef(\___genetic_Pattern).stop;
		// This is pretty hacky...
		this.say("no one seems to be listening... I'm just going to play around...");
		Routine{
			"starting generative pattern".postln;
			4.wait;
			while({this.inStandby},{
				var mel = Genetic.wChooseN(this.population,1)[0].value;
				var waitTime=0;
				("__ playing: "+mel).postln;
				this.sayMelody(mel);
				if(mel.durations.size>=mel.notes.size,{
					waitTime = mel.durations.sum;
					Pbind(\instrument,\bell,\midinote,Pseq(mel.notes,inf),\dur,Pseq(mel.durations,1)).play;
				},{
					// waitTime = (mel.durations!((mel.notes.size/mel.durations.size).floor+1)).flatten.sum;
					mel.notes.do({|v,i|waitTime=waitTime+mel.durations[i%mel.durations.size]});
					Pbind(\instrument,\bell,\midinote,Pseq(mel.notes,1),\dur,Pseq(mel.durations,inf)).play;
				});
				waitTime.wait;
			});
		}.play;
	}



	step{
		var a;
		var index;
		var i = this.population.size;
		var f;

		a = Genetic.wChooseN(this.population,1)[0];
		// will this ever loop forever? - what if they're all the same?
		// should be an exit case where it takes one, breeds a new population w/ mutations
		while({(a==this.lastSelected) && (i>0)},{
			a = Genetic.wChooseN(this.population,1)[0];
			i=i-1;
			if((i==0),{"population is identical".warn});
		});
		this.lastSelected = a;
		this.history.add(a);
		this.history = this.history[this.history.size-20..this.history.size]; // only keep last 20 individuals
		//NOTE: this has to go up here before the aiFitnessFunc or else you'll get an infinite value for the longFrequencyComponent
		this.generations = this.generations+1;

		index = this.population.collect({|v,i|v}).indexOf(a);

		Pdef(\___genetic_Pattern,a.value.pattern).play;
		this.say(["How do you feel about this melody?","How's this melody?","What about this melody?"].choose);
		5.wait;
		this.say("(smile for approval - frown for disapproval!)");
		this.sayMelody(a.value);
		3.wait;
		f = this.fitnessFunc.value(a.value);

		// If the fitness func returns a rating indicating no rating (nil) then set standbyMode on
		// otherwise continue transforming/making new melodies
		if( f==nil,{
			this.inStandby=true;
		},{

			f = (f*0.5)+(this.aiFitnessFunc(a.value,f)*0.5);
			Pdef(\___genetic_Pattern).stop;
			this.population[index].fitness=f;
			this.memory.at(a.hash).add(f);
		});
	}

	aiFitnessFunc {
		|melody, audienceRating|
		var frequencyComponent;
		var variance;
		var pastRatingComponent;
		var rating = 0;
		var memory = this.memory.at(melody.hash); // shouldn't have to error check here, if the melody has made it in here, it should exist in the dictionary already...
		var recentFrequency=0;
		var longFrequency;
		var string;
		// Frequency played - if it's been played a lot downvote
		// how should long frequency be weighted against short history?
		this.history.do({|i|
			if (i.value.hash==melody.value.hash,{recentFrequency=recentFrequency+1});
		});
		recentFrequency = recentFrequency/this.history.size;


		longFrequency = memory.count/this.generations;

		("(((((((((((((((((((    long freq: "+longFrequency).postln;
		(")))))))))))))))))))    short freq: "+recentFrequency).postln;
		// try a 20%/80% split
		frequencyComponent = 1-(0.2*longFrequency+(0.8*recentFrequency));


		case
		{frequencyComponent>0.75} {this.say(["Wow I don't know if I've ever heard this before!","This melody is unique!"].choose)}
		{frequencyComponent>0.5} {this.say(["This one is pretty new...","Hmm, this is pretty unique..."].choose)}
		{frequencyComponent>0.25} {this.say(["This melody has been played a lot, I'm kind of tired of it...","I'm kind of getting tired of this one..."].choose)}
		{frequencyComponent>=0} {this.say(["Ahh this melody is overplayed...","I've heard this melody too much!"].choose)};

		6.wait;
		// Past average -
		pastRatingComponent = memory.fitnessAverage;

		case {pastRatingComponent>0.75} {this.say(["Everyone before has loved this one!","This has been a popular one in the past!"].choose)}
		{pastRatingComponent>0.5} {this.say(["I seem to remember people tend to like this melody...","People tend to like this one..."].choose)}
		{pastRatingComponent>0.25} {this.say(["If I remember correctly this one hasn't been very popular...","I don't think people usually like this one very much..."].choose)}
		{pastRatingComponent>=0} {this.say(["Most people really dislike this melody...","This one has never been a crowd pleaser..."].choose)};

		6.wait;


		this.say("You thought this melody was about:"+(audienceRating*10)+"/ 10");
		5.wait;

		// Audience compoennt - if there has been a historically indecisive crowd on this melody, don't give the
		// Audience much weight...
		// Variance of past ratings - indecisive->don't factor in audience decision too much.
		variance = memory.fitnessVariance;
		case {variance>0.75} {this.say(["But hardly anyone agrees on this one, I'm not really going to take that into consideration..."].choose)}
		{variance>0.5} {this.say(["But people have been pretty indecisive on this, I'm not going to take that too seriously..."].choose)}
		{variance>0.25} {this.say(["And past audiences have tended to agreed on this..."].choose)}
		{variance>=0} {this.say(["And past audiences have been very decisive about this melody..."].choose)};
		6.wait;


		string = ["hmmm","*thinking*"].choose;
		4.do{
			|i|
			var s="";
			i.do{s=s++".";};//uhg
			this.say(string++s);
			1.wait;
		};


		//frequency: both long and short history,  pastRating: previous opinion, audienceRating: weighted by variance of past
		rating = ([1/3,1/3,(1-(variance*variance))/3].normalizeSum*[frequencyComponent, pastRatingComponent, audienceRating]).sum;
		// rating = [frequencyComponent,audienceComponent,pastRatingComponent].mean;


		this.say("Based on all that my overall impression of this melody is:"+(rating*10)+"/ 10!");
		Pdef(\___genetic_Pattern).stop;

		5.wait;
		("audienceComponent:   " +audienceRating).postln;
		("frequencyComponent:   " +frequencyComponent).postln;
		("pastRatingComponent:   " +pastRatingComponent).postln;
		("morrre"+([1/3,1/3,(1-(variance*variance))/3].normalizeSum*[frequencyComponent, pastRatingComponent, audienceRating])).postln;
		(">>>>>>>>>>> FINAL RATING:    "+rating).postln;

		// Routine{
		// 	case {audienceComponent>0.75} {this.say("Wow you really like that one!")}
		// 	{audienceComponent>0.5} {this.say("Hey you like that one, I'll make note!")}
		// 	{audienceComponent>0.25} {this.say("Hmm that one wasn't so great was it?")}
		// 	{audienceComponent>0} {this.say("Yikes, won't play that one again....")};
		//
		// 	6.wait;
		//
		//
		// case {frequencyComponent>0.75} {this.say("This one hasn't even really been played before!")}
		//
		// {frequencyComponent>0.5} {this.say("This one is pretty unique!")}
		//
		// {frequencyComponent>0.25} {this.say("Hmmm this one has been repeated a few times though I'm kind of tired of it.")}
		//
		// {frequencyComponent>0} {this.say("I've heard this one way too much!")};


		// 	2.wait;
		// 	this.say("My final rating for this melody is......");
		// 	2.wait;
		// 	this.say(rating.asString);
		// 	2.wait;
		//
		// }.play;

		^rating;
	}

	showPopulation{
		this.population.do{
			|i|
			i.postln;
		};
	}

}

// TODO:
// Somthing to decide - should history/frequency played really affect current fitness? or should it just factor into which melody is selected next?