
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Hashtable;
import java.util.LinkedList;

public class Simulate {

   public final int PAGE_SIZE = 2048; // 2 KB

   // Opt
   public void opt(int nFrames, String traceFileName) {
      int totalMemAccesses = 0;
      int totalPageFaults = 0;
      int totalWritesToDisk = 0;
      char instructionType = ' '; // Instruction (I)
      char accessType = ' '; // Load (L), Store (S), Modify (M)
      int[] pageFrames = new int[nFrames];
      Hashtable<Integer, PageTableEntry> pageTable = new Hashtable<>();
      Hashtable<Integer, LinkedList<Integer>> futureMap = new Hashtable<>(); // Linked list or I guess i could have used
      // ArrayList to dynamically allocate this
      // instead of setting them all to max size
      BufferedReader r = null;
      String line = "";
      // Read and parse the trace file
      try {
        //Need to init pageTable and futureMap to correct size
         for (int i = 0; i < 2097152; i++) { // 32 bit address space / 2KB pageSize = # of pages
            // Initialize the page table & future hashtables.
            PageTableEntry pte = new PageTableEntry();
            pageTable.put(i, pte);
            futureMap.put(i, new LinkedList<Integer>());
         }
         // Initialize the page_frames array.
         for (int i = 0; i < nFrames; i++) {
            pageFrames[i] = -1;
         }

         // We need to set futureTable  we can do opt after this
         r = new BufferedReader(new FileReader(traceFileName));
         int counter = 0;
         int lineCount = 0;

         while (r.ready()) {
            line = r.readLine();
            // This is 2 get past headers and the end of file
            if (line.startsWith("==")) {
               if (line.equals("==632==")) {
                  counter++;
                  if (counter == 1) {
                     break;
                  }
               }
               continue; // Skip empty lines and header lines
            }
            String[] parts = line.split(" ");
            int pageNumber = Integer.decode("0x" + parts[2].substring(0, 5));
            futureMap.get(pageNumber).add(lineCount); // Add the line count everytime a pageNum is refrenced
            lineCount++;
         }
         int currentFrame = 0;
         int counter2 = 0;
         r = new BufferedReader(new FileReader(traceFileName));
         while (r.ready()) { // Can do opt now
            line = r.readLine();
            // This is 2 get past headers and rthe end of file
            if (line.startsWith("==")) {
               if (line.equals("==632==")) {
                  counter2++;
                  if (counter2 == 1) {
                     break;
                  }
               }
               continue; // Skip empty lines and header lines
            }
            // Parse the line
            String[] parts = line.split(" ");
            if (!parts[1].isEmpty()) {
               accessType = parts[1].charAt(0); // Load (L), Store (S), Modify (M)
            }
            if (!parts[0].isEmpty()) {
               instructionType = parts[0].charAt(0); // I
            }

            // Convert hex to decimal page number
            int pageNumber = Integer.decode("0x" + parts[2].substring(0, 5));

            futureMap.get(pageNumber).removeFirst();
            PageTableEntry pte = pageTable.get(pageNumber);
            pte.index = pageNumber;
            pte.referenced = true; // Always set ref to true

            // If valid hit else set it to valid and pagefault
            if (instructionType == 'I') {
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) { // If we have room
                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else { // No room eviction algo
                     int longestDistance = locateLongestDistancePage(pageFrames, futureMap);
                     PageTableEntry tPte = pageTable.get(longestDistance); // Page to kick based on longest distance

                     if (tPte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     // Swap out the page
                     pageFrames[tPte.frame] = pte.index;
                     pte.frame = tPte.frame;
                     pte.valid = true;
                     tPte.dirty = false;
                     tPte.referenced = false;
                     tPte.valid = false;
                     tPte.frame = -1;
                     pageTable.put(longestDistance, tPte);
                  }
               } else {
                  System.out.println("HIT");
               }
            }
            // S means dirty bit needs to be true and valid if not already a valid hit
            if (instructionType == 'S') {
               pte.dirty = true; // Dirty since its a store
               if (!pte.valid) { // if its not valid pagefault and make it valid
                  totalPageFaults++;
                  pte.valid = true;

                  if (currentFrame < nFrames) { // if we have room
                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else { // Else run the eviction algo
                     int longestDistance = locateLongestDistancePage(pageFrames, futureMap);
                     PageTableEntry t_pte = pageTable.get(longestDistance);

                     if (t_pte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }

                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(longestDistance, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
            }
            if (accessType == 'L') { // Same as I
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {

                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;

                  } else {

                     int longestDistance = locateLongestDistancePage(pageFrames, futureMap);
                     PageTableEntry t_pte = pageTable.get(longestDistance);

                     if (t_pte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }

                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     // pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(longestDistance, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
            }

            if (accessType == 'M') { // M is a L followed by S
               // Load
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {

                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {

                     int longestDistance = locateLongestDistancePage(pageFrames, futureMap);
                     PageTableEntry t_pte = pageTable.get(longestDistance);

                     if (t_pte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }

                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     // pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(longestDistance, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
               // Store
               pte.dirty = true;
               pte.valid = true;
               // Mem access since we did load and store
               totalMemAccesses++;
            }
            pageTable.put(pageNumber, pte);// update pageTable in the end
            totalMemAccesses++;// Mem Access
         }

         System.out.println("Algorithm: OPT");
         System.out.println("Number of frames:\t" + nFrames);
         System.out.println("Total memory accesses:\t" + totalMemAccesses);
         System.out.println("Total page faults:\t" + totalPageFaults);
         System.out.println("Total writes to disk:\t" + totalWritesToDisk);
      } catch (Exception e) {
         e.printStackTrace();

      }

   }

  //We alrdy know the future so we just locate the furthest from use page
   private static int locateLongestDistancePage(int[] page_frames, Hashtable<Integer, LinkedList<Integer>> futureMap) {
      int index = 0;
      int max = 0;
      for (int i = 0; i < page_frames.length; i++) {
         if (futureMap.get(page_frames[i]).isEmpty()) {
            return page_frames[i];
         } else {
            if (futureMap.get(page_frames[i]).get(0) > max) { 
               max = futureMap.get(page_frames[i]).get(0);
               index = page_frames[i];
            }
         }
      }
      return index;
   }

   // Clock
   public void clock(int nFrames, String traceFileName) {
      int totalMemAccesses = 0;
      int totalPageFaults = 0;
      int totalWritesToDisk = 0;
      int clockHandPos = 0;
      char instructionType = ' '; // Instruction (I)
      char accessType = ' '; // Load (L), Store (S), Modify (M)
      Hashtable<Integer, PageTableEntry> pageTable = new Hashtable<Integer, PageTableEntry>();
      int[] pageFrames = new int[nFrames];
      BufferedReader r = null;
      String line = " ";
      try { // Initilize page table to #pages
         for (int i = 0; i < 2097152; i++) {
            PageTableEntry pte = new PageTableEntry();
            pageTable.put(i, pte);
         }
         // Initialize the page_frames array.
         for (int i = 0; i < nFrames; i++) {
            pageFrames[i] = -1;
         }

         /**
          * Start executing the clock page replacement algorithm.
          */
         int currentFrame = 0;
         int counter = 0;
         r = new BufferedReader(new FileReader(traceFileName));
         while (r.ready()) {
            line = r.readLine();
            // This is 2 get past headers and rthe end of file
            if (line.startsWith("==")) {
               if (line.equals("==632==")) {
                  counter++;
                  if (counter == 1) {
                     break;
                  }
               }
               continue; // Skip empty lines and header lines
            }

            String[] parts = line.split(" ");
            if (!parts[1].isEmpty()) {
               accessType = parts[1].charAt(0); // Load (L), Store (S), Modify (M)
            }
            if (!parts[0].isEmpty()) {
               instructionType = parts[0].charAt(0); // I
            }

            // Convert hex to decimal page number
            int pageNumber = Integer.decode("0x" + parts[2].substring(0, 5));

            PageTableEntry pte = pageTable.get(pageNumber);
            pte.index = pageNumber;
            pte.referenced = true; // We always refrence

            if (instructionType == 'I') { // If valid hit else make valid we page faulted
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) { // If we have room

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     // Clock algo
                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) { // While page is ref keep looking
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {

                           // If ref == false found page to evict

                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {

                           // if the reference bit is 1 clear it

                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }

                        // Increment clock 

                        clockHandPos++;
                     }

                     PageTableEntry t_pte = pageTable.get(page_num_to_evict);
                     if (t_pte.dirty) {

                        // If (swapped out page is dirty) increment the write to disk number;

                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     // swap
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     t_pte.dirty = false;
                     t_pte.referenced = true;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
            }
            // S means dirty bit needs to be true and valid if not already a valid hit
            if (instructionType == 'S') {
               pte.dirty = true;// Dirty == true because S
               if (!pte.valid) { // If not valid pagefault and make valid else hit
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {// If room

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     // Clock algo
                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) {
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {

                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {

                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }

                        clockHandPos++;
                     }

                     PageTableEntry t_pte = pageTable.get(page_num_to_evict);
                     if (t_pte.dirty) {

                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     // swap
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     // pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = true;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
            }
            if (accessType == 'L') { // Same as I
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {

                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) {
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {

                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {

                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }

                        clockHandPos++;
                     }

                     PageTableEntry t_pte = pageTable.get(page_num_to_evict);
                     if (t_pte.dirty) {
                        /**
                         * If (swapped out page is dirty) increment the write to disk number;
                         */
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     // Swap
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
            }

            if (accessType == 'M') { // L and then a S
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {

                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) {
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {

                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {

                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }

                        clockHandPos++;
                     }

                     PageTableEntry t_pte = pageTable.get(page_num_to_evict);
                     if (t_pte.dirty) {

                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }

                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
               }
               pte.dirty = true;
               pte.valid = true;
               totalMemAccesses++;
            }
            //Put the new entry in pageTable and ++ memAccess for each line
            pageTable.put(pageNumber, pte);
            totalMemAccesses++;
         }
         /**
          * Print statistics.
          */
         System.out.println("Algorithm: CLOCK");
         System.out.println("Number of frames:\t" + nFrames);
         System.out.println("Total memory accesses:\t" + totalMemAccesses);
         System.out.println("Total page faults:\t" + totalPageFaults);
         System.out.println("Total writes to disk:\t" + totalWritesToDisk);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   // NRU
   public void nru(int nFrames, int refresh, String traceFileName) {
      int totalMemAccesses = 0;
      int totalPageFaults = 0;
      int totalWritesToDisk = 0;
      char instructionType = ' '; // Instruction (I)
      char accessType = ' '; // Load (L), Store (S), Modify (M)
      Hashtable<Integer, PageTableEntry> pageTable = new Hashtable<Integer, PageTableEntry>();
      int[] pageFrames = new int[nFrames];
      BufferedReader r = null;
      try {
         System.out.println("Initializing page table...");
         for (int i = 0; i < 2097152; i++) {
            PageTableEntry pte = new PageTableEntry();
            pageTable.put(i, pte);
         }
         // Initialize the page_frames array.
         for (int i = 0; i < nFrames; i++) {
            pageFrames[i] = -1;
         }

         /**
          * Start executing the nru page replacement algorithm.
          */
         int currentFrame = 0;
         String line = " ";
         int counter = 0;
         r = new BufferedReader(new FileReader(traceFileName));
         while (r.ready()) {
            line = r.readLine();
            if (line.startsWith("==")) {
               if (line.equals("==632==")) {
                  counter++;
                  if (counter == 1) {
                     break;
                  }
               }
               continue; // Skip empty lines and header lines
            }
            // We can just reset ref right at the start same idea
            if (totalMemAccesses % refresh == 0) {
               for (int i = 0; i < currentFrame; i++) {
                  PageTableEntry pte = pageTable.get(pageFrames[i]);
                  pte.referenced = false;
                  pageTable.put(pte.index, pte);
               }
            }

            String[] parts = line.split(" ");
            int pageNumber = Integer.decode("0x" + parts[2].substring(0, 5));
            PageTableEntry pte = pageTable.get(pageNumber);

            if (!parts[1].isEmpty()) {
               accessType = parts[1].charAt(0); // Load (L), Store (S), Modify (M)

            }
            if (!parts[0].isEmpty()) {
               instructionType = parts[0].charAt(0); // I
            }

            pte.index = pageNumber;
            pte.referenced = true;// We always set ref since pte has been ref
            if (instructionType == 'I') { // If I and valid hit else page fault and set valid
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {// if we have room

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     // Run NRU
                     PageTableEntry page_to_evict = null;
                     int done_flag = 0;

                     // Divide all the pages into 4 categories if page faults
                     while (done_flag == 0) {
                        for (int p_frame = 0; p_frame < pageFrames.length; p_frame++) {

                           PageTableEntry temp_pte = pageTable.get(pageFrames[p_frame]);

                           if (!temp_pte.referenced && !temp_pte.dirty && temp_pte.valid) { // If its this category just
                                                                                            // swap instantly
                              // not referenced, not modified
                              pte.frame = temp_pte.frame;

                              System.out.println(parts[0] + " (page fault – evict clean)");

                              pageFrames[pte.frame] = pte.index;
                              temp_pte.valid = false;
                              temp_pte.dirty = false;
                              temp_pte.referenced = false;
                              temp_pte.frame = -1;
                              pageTable.put(temp_pte.index, temp_pte);
                              pageTable.put(pte.index, pte);
                              done_flag = 1;
                              break;
                           } else {
                              if (!temp_pte.referenced && temp_pte.dirty && temp_pte.valid) {
                                 // not referenced, modified
                                 page_to_evict = new PageTableEntry(temp_pte);
                                 continue;
                              } else {
                                 if (temp_pte.referenced && !temp_pte.dirty && temp_pte.valid
                                       && page_to_evict == null) {
                                    // referenced, not modified
                                    page_to_evict = new PageTableEntry(temp_pte);
                                    continue;
                                 } else {
                                    if (temp_pte.referenced && temp_pte.dirty && temp_pte.valid
                                          && page_to_evict == null) {
                                       // referenced, modified
                                       page_to_evict = new PageTableEntry(temp_pte);
                                       continue;
                                    }
                                 }
                              }
                           }
                        }

                        if (done_flag == 1) {
                           continue;
                        }

                        pte.frame = page_to_evict.frame;

                        if (page_to_evict.dirty) {
                           /**
                            * If (swapped out page is dirty) increment the write to disk number;
                            */
                           System.out.println(parts[0] + " (page fault – evict dirty)");
                           totalWritesToDisk++;
                        } else {
                           System.out.println(parts[0] + " (page fault – evict clean)");
                        }
                        // Swap the chosen page
                        pageFrames[pte.frame] = pte.index;
                        page_to_evict.valid = false;
                        page_to_evict.dirty = false;
                        page_to_evict.frame = -1;
                        page_to_evict.referenced = false;
                        pageTable.put(page_to_evict.index, page_to_evict);
                        pageTable.put(pte.index, pte);
                        done_flag = 1;
                     }
                  }

               } else {
                  System.out.println("HIT");
               }
            }
            if (instructionType == 'S') {
               pte.dirty = true;// Store so dirty
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;

                  if (currentFrame < nFrames) {// if we have room

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     // Nru algo
                     PageTableEntry page_to_evict = null;
                     int done_flag = 0;

                     // Categorize all the pte
                     while (done_flag == 0) {
                        for (int p_frame = 0; p_frame < pageFrames.length; p_frame++) {

                           PageTableEntry temp_pte = pageTable.get(pageFrames[p_frame]);

                           if (!temp_pte.referenced && !temp_pte.dirty && temp_pte.valid) {
                              // not referenced, not modified
                              pte.frame = temp_pte.frame;

                              System.out.println(parts[0] + " (page fault – evict clean)");

                              pageFrames[pte.frame] = pte.index;
                              temp_pte.valid = false;
                              temp_pte.dirty = false;
                              temp_pte.referenced = false;
                              temp_pte.frame = -1;
                              pageTable.put(temp_pte.index, temp_pte);
                              pageTable.put(pte.index, pte);
                              done_flag = 1;
                              break;
                           } else {
                              if (!temp_pte.referenced && temp_pte.dirty && temp_pte.valid) {
                                 // not referenced, modified
                                 page_to_evict = new PageTableEntry(temp_pte);
                                 continue;
                              } else {
                                 if (temp_pte.referenced && !temp_pte.dirty && temp_pte.valid
                                       && page_to_evict == null) {
                                    // referenced, not modified
                                    page_to_evict = new PageTableEntry(temp_pte);
                                    continue;
                                 } else {
                                    if (temp_pte.referenced && temp_pte.dirty && temp_pte.valid
                                          && page_to_evict == null) {
                                       // referenced, modified
                                       page_to_evict = new PageTableEntry(temp_pte);
                                       continue;
                                    }
                                 }
                              }
                           }
                        }

                        if (done_flag == 1) {
                           continue;
                        }

                        pte.frame = page_to_evict.frame;

                        if (page_to_evict.dirty) {

                           System.out.println(parts[0] + " (page fault – evict dirty)");
                           totalWritesToDisk++;
                        } else {
                           System.out.println(parts[0] + " (page fault – evict clean)");
                        }
                        // swap
                        pageFrames[pte.frame] = pte.index;
                        page_to_evict.valid = false;
                        page_to_evict.dirty = false;
                        page_to_evict.frame = -1;
                        page_to_evict.referenced = false;
                        pageTable.put(page_to_evict.index, page_to_evict);
                        pageTable.put(pte.index, pte);
                        done_flag = 1;
                     }
                  }

               } else {
                  System.out.println("HIT");
               }
            }
            if (accessType == 'L') {// Same thing as I
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {

                     PageTableEntry page_to_evict = null;
                     int done_flag = 0;

                     while (done_flag == 0) {
                        for (int p_frame = 0; p_frame < pageFrames.length; p_frame++) {

                           PageTableEntry temp_pte = pageTable.get(pageFrames[p_frame]);

                           if (!temp_pte.referenced && !temp_pte.dirty && temp_pte.valid) {
                              // not referenced, not modified
                              pte.frame = temp_pte.frame;
                              if (temp_pte.dirty) {
                                 System.out.println(parts[0] + " (page fault – evict dirty)");
                                 totalWritesToDisk++;
                              } else {
                                 System.out.println(parts[0] + " (page fault – evict clean)");
                              }
                              pageFrames[pte.frame] = pte.index;
                              temp_pte.valid = false;
                              temp_pte.dirty = false;
                              temp_pte.referenced = false;
                              temp_pte.frame = -1;
                              pageTable.put(temp_pte.index, temp_pte);
                              pageTable.put(pte.index, pte);
                              done_flag = 1;
                              break;
                           } else {
                              if (!temp_pte.referenced && temp_pte.dirty && temp_pte.valid) {
                                 // not referenced, modified
                                 page_to_evict = new PageTableEntry(temp_pte);
                                 continue;
                              } else {
                                 if (temp_pte.referenced && !temp_pte.dirty && temp_pte.valid
                                       && page_to_evict == null) {
                                    // referenced, not modified
                                    page_to_evict = new PageTableEntry(temp_pte);
                                    continue;
                                 } else {
                                    if (temp_pte.referenced && temp_pte.dirty && temp_pte.valid
                                          && page_to_evict == null) {
                                       // referenced, modified
                                       page_to_evict = new PageTableEntry(temp_pte);
                                       continue;
                                    }
                                 }
                              }
                           }
                        }

                        if (done_flag == 1) {
                           continue;
                        }

                        pte.frame = page_to_evict.frame;

                        if (page_to_evict.dirty) {

                           System.out.println(parts[0] + " (page fault – evict dirty)");
                           totalWritesToDisk++;
                        } else {
                           System.out.println(parts[0] + " (page fault – evict clean)");
                        }
                        // swap
                        pageFrames[pte.frame] = pte.index;
                        page_to_evict.valid = false;
                        page_to_evict.dirty = false;
                        page_to_evict.frame = -1;
                        page_to_evict.referenced = false;
                        pageTable.put(page_to_evict.index, page_to_evict);
                        pageTable.put(pte.index, pte);
                        done_flag = 1;
                     }
                  }

               } else {
                  System.out.println("HIT");
               }
            }
            if (accessType == 'M') { // L then S
               // Load
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {

                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {

                     PageTableEntry page_to_evict = null;
                     int done_flag = 0;

                     while (done_flag == 0) {
                        for (int p_frame = 0; p_frame < pageFrames.length; p_frame++) {

                           PageTableEntry temp_pte = pageTable.get(pageFrames[p_frame]);

                           if (!temp_pte.referenced && !temp_pte.dirty && temp_pte.valid) {
                              // not referenced, not modified
                              pte.frame = temp_pte.frame;
                              if (temp_pte.dirty) {
                                 System.out.println(parts[0] + " (page fault – evict dirty)");
                                 totalWritesToDisk++;
                              } else {
                                 System.out.println(parts[0] + " (page fault – evict clean)");
                              }
                              pageFrames[pte.frame] = pte.index;
                              temp_pte.valid = false;
                              temp_pte.dirty = false;
                              temp_pte.referenced = false;
                              temp_pte.frame = -1;
                              pageTable.put(temp_pte.index, temp_pte);
                              pageTable.put(pte.index, pte);
                              done_flag = 1;
                              break;
                           } else {
                              if (!temp_pte.referenced && temp_pte.dirty && temp_pte.valid) {
                                 // not referenced, modified
                                 page_to_evict = new PageTableEntry(temp_pte);
                                 continue;
                              } else {
                                 if (temp_pte.referenced && !temp_pte.dirty && temp_pte.valid
                                       && page_to_evict == null) {
                                    // referenced, not modified
                                    page_to_evict = new PageTableEntry(temp_pte);
                                    continue;
                                 } else {
                                    if (temp_pte.referenced && temp_pte.dirty && temp_pte.valid
                                          && page_to_evict == null) {
                                       // referenced, modified
                                       page_to_evict = new PageTableEntry(temp_pte);
                                       continue;
                                    }
                                 }
                              }
                           }
                        }

                        if (done_flag == 1) {
                           continue;
                        }

                        pte.frame = page_to_evict.frame;

                        if (page_to_evict.dirty) {

                           System.out.println(parts[0] + " (page fault – evict dirty)");
                           totalWritesToDisk++;
                        } else {
                           System.out.println(parts[0] + " (page fault – evict clean)");
                        }
                        // Swap
                        pageFrames[pte.frame] = pte.index;
                        page_to_evict.valid = false;
                        page_to_evict.dirty = false;
                        page_to_evict.frame = -1;
                        page_to_evict.referenced = false;
                        pageTable.put(page_to_evict.index, page_to_evict);
                        pageTable.put(pte.index, pte);
                        done_flag = 1;
                     }
                  }
               } else {
                  System.out.println("HIT");
               }
               //Store
               pte.dirty = true;
               pte.valid = true;
               totalMemAccesses++;
            }
            pageTable.put(pageNumber, pte);
            totalMemAccesses++;

         }
         System.out.println("Algorithm: NRU");
         System.out.println("Number of frames:\t" + nFrames);
         System.out.println("Total memory accesses:\t" + totalMemAccesses);
         System.out.println("Total page faults:\t" + totalPageFaults);
         System.out.println("Total writes to disk:\t" + totalWritesToDisk);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}