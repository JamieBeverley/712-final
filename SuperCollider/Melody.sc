Melody{
	classvar <>noteList;
	var <>notes;
	var <>durations;
	var <>instruments;

	*initClass{
		noteList = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];
	}

	*new{
		|notes,durations,instruments|
		if((notes==nil),{notes=[]});
		if((durations==nil||durations==[]),{durations=[1]});
		if((instruments==nil),{instruments=[\default]});
		^super.new.init(notes,durations,instruments);
	}


	*toNote{
		|i|
		var octave = ((i/12).floor)-1;
		var note = Melody.noteList[i%12];
		^(note++octave).asSymbol;
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


	asNotes{
		^this.notes.collect({|v|Melody.toNote(v)});
	}

	showNotes{
		var noteList = this.asNotes;
		var str="";
		noteList.do{
			|i|
			str = str++i.asString++" ";
		};
		^str;
	}

	asString{
		^("Melody(notes: "++this.notes++", durs: "++this.durations++", instruments: "++this.instruments++")");
	}

	hash{
		^(this.notes.asString);
	}

}