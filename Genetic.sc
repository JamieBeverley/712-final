Genetic {
	//max population size implicit in the initial pop.
	var <>replacementSize; // # of new individuals created by breed, cross, and mutation, and # of least fit removed from prior gen.
	var <>population;  // should be a list of Individuals (from Individual.sc) - a fitness and value pair
	var <>fitnessFunc; // assigns a fitness rating for each individual in a population (from 0 to 1)
	var <>mutateFunc; // maybe this can be part of the below 'breedFunc'
	var <>breedFunc; // a func taking: [[fitness (0-1), individual]], returning a population ([individual])
	var <>lastSelected;
	var <>history;
	var <>scale;
	var <>maxMelodyLength; //length in the 'notes' list
	var <>minMelodyLength; //length in the 'notes' list

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
		this.history = Dictionary.new();

		this.population.do({|i|
			"hmmm".postln;
			this.history.add(i.hash->Memory.new(i.value,i.fitness));
		});
		this.scale = scale;
		this.maxMelodyLength = maxMelodyLength;
		this.minMelodyLength = minMelodyLength;

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

			// Add child to list that re-populates population, and to history dictionary
			children.add(child);
			"before all".postln;
			// if the child has already been played at some point, add the fitness to it's history
			// otherwise create a new entry and add it into the memroy dictionary.
			if( (this.history.at(child.hash)==nil),{
				"this.history.child.hash==nil".postln;
				this.history.add(child.hash->Memory.new(value:child.value,fitness:child.fitness));
			},{
				"this.history.child.hash!=nil".postln;
				this.history.at(child.hash).add(fitness:child.fitness)
			});

		};


		this.population = (this.population++children);

	}

	mutate{
		|mother, father, child|
		var mutateFactor=0;
		var variances = List.new;
		var variancesLength = mother.value.notes.size;
		var lengthFactor; //for lengthening/shortening the melody
		var durLengthFactor;



		// Sort of detecting for 'inbreeding' - when mother and father are very similar, more mutation
		min(mother.value.notes.size,father.value.notes.size).do{
			|i|
			variances.add((mother.value.notes[i]-father.value.notes[i]).abs);
		};
		variances.do{
			|i|
			mutateFactor = mutateFactor+(i/((this.scale.maxItem)-(this.scale.minItem)));
		};

		mutateFactor = 1 - (mutateFactor/variances.size);

		// Exponential - things that are just slightly 'inbred' aren't mutated too often, more inbreeding with super similar ones
		mutateFactor = mutateFactor**4;

		("### Mother:  "+mother).postln;
		("### Father:  "+father).postln;
		("### Mutate Factor:   "+mutateFactor).postln;
		("### Child melody:   "+child.value.notes).postln;

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

		"here".postln;

		durLengthFactor = child.value.durations.size/this.maxMelodyLength;

		if(durLengthFactor <= 0.5,{
/*			child.value.durations = child.value.durations.collect({|v,i| [v,[v,[]].wchoose([mutateFactor*durLengthFactor,1-
			(mutateFactor*durLengthFactor)].normalizeSum)]}).flatten.flatten;*/
			">>>>>>>>>>>>>>>>.>>>>>>>>>>>>>>>>>.>>>>>>>> going to grow the duration list".postln;
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
		"here2".postln;
		if ((lengthFactor<=0.5),{
			child.value.notes = child.value.notes.collect({|v,i| [v,[v,[]].wchoose([mutateFactor*lengthFactor,1-(mutateFactor*lengthFactor)].normalizeSum)]}).flatten.flatten;
		},{
			child.value.notes = child.value.notes.collect({|v,i| [v,[]].wchoose([1-(mutateFactor*(1-lengthFactor)),mutateFactor*(1-lengthFactor)].normalizeSum)}).flatten;
		});
		"here3".postln;

		("### Mutant melody notes:   "+child.value.notes).postln;


		^child;
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
			"PICKED SAME TWICE".postln;
			(">>>>>>> "+i).postln;
			if((i==0),{"population is identical".warn});
		});
		this.lastSelected = a;
		index = this.population.collect({|v,i|v}).indexOf(a);

		Pdef(\___genetic_Pattern,a.value.pattern).play;
		f = this.fitnessFunc.value(a.value);
		Pdef(\___genetic_Pattern).stop;
		this.population[index].fitness=f;
		this.history.at(a.hash).add(f);

	}


	showPopulation{
		this.population.do{
			|i|
			i.postln;
		};
	}

}