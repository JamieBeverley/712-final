Individual{
	var <>fitness;
	var <>value;

	*new{
		|fitness,value|
		^super.new.init(fitness,value);
	}

	init{
		|fitness,value|
		this.fitness = fitness;
		this.value = value;
	}

	asString{
		^("Individual(fitness: "++this.fitness++", value: "++this.value++")");
	}

	hash{
		^this.value.hash;
	}

}