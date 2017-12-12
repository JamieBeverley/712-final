Melody{
	var <>notes;
	var <>durations;
	var <>instruments;

	*new{
		|notes,durations,instruments|
		if((notes==nil),{notes=[]});
		if((durations==nil||durations==[]),{durations=[1]});
		if((instruments==nil),{instruments=[\default]});
		^super.new.init(notes,durations,instruments);
	}

	init{
		|notes,durations,instruments|
		this.notes=notes;
		this.durations=durations;
		this.instruments = instruments
		^this;
	}

	pattern{
		//is there any clean up I have to manage?
		^Pbind(\instrument,Pseq(this.instruments,inf), \midinote,Pseq(this.notes,inf),\dur,Pseq(this.durations,inf),\legato,1);
	}


	asString{
		^("Melody(notes: "++this.notes++", durs: "++this.durations++", instruments: "++this.instruments++")");
	}

	hash{
		^(this.notes.asString);
	}

}