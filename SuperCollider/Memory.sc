Memory{
	var <>value;
	var <>scores;

	*new{
		|value,fitness|

		^super.new.init(value,fitness);
	}

	init{
		|value,fitness|
		this.value=value;
		this.scores = List.new();
		this.scores.add(fitness);
	}

	add{
		|fitness|
		("#######################  added:    "+fitness).postln;
		this.scores.add(fitness);
	}

	fitnessAverage{
		^this.scores.mean;
	}

	fitnessVariance{
		var dev = 0;
		this.scores.do({|i|dev=dev+(i*i)});
		dev = dev/this.scores.size;
		dev = dev -(this.scores.mean**2);
		^dev;
	}

	count{
		^this.scores.size;
	}
}
