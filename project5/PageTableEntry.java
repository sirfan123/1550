//package project5;

public class PageTableEntry {

    int index;
    int frame; //Cant we delete index and frame and just do frame#
    boolean valid;
    boolean dirty;
    boolean referenced;
 
    public PageTableEntry() {
       this.index = 0;
       this.frame = -1;
       this.valid = false;
       this.dirty = false;
       this.referenced = false;
    }
 
    public PageTableEntry(PageTableEntry copy) {
       this.index = copy.index;
       this.frame = copy.frame;
       this.valid = copy.valid;
       this.dirty = copy.dirty;
       this.referenced = copy.referenced;
    }
 }