
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class Simulate {

   /**
    * Simulate class constructor.
    */
   public Simulate() {

   }

   public final int PAGE_SIZE = 2048; // 2 KB

   // Initialize page frames //S MEANS DIRTY BIT and VALID NEEDS TO BE SET 2 ONE
   // M IS JUST A L AND THEN A STORE
   // I AND L make invalid page valid and make it valid but if alrdy valid its a
   // hit and set refrenced
   // Just chop bottom 11 bits off index[2] my hex
   public void opt(int nFrames, String traceFileName) {
      int totalMemAccesses = 0;
      int totalPageFaults = 0;
      int totalWritesToDisk = 0;
      char instructionType = ' '; // Instruction (I)
      char accessType = ' '; // Load (L), Store (S), Modify (M)
      int[] pageFrames = new int[nFrames];
      Hashtable<Integer, PageTableEntry> pageTable = new Hashtable<>();
      Hashtable<Integer, LinkedList<Integer>> future = new Hashtable<>();
      BufferedReader br = null;
      String line = "";
      // Read and parse the trace file
      try {
         System.out.println("Initializing page table...");
         for (int i = 0; i < 1024 * 1024; i++) {
            // Initialize the page table & future hashtables.
            PageTableEntry pte = new PageTableEntry();
            pageTable.put(i, pte);
            future.put(i, new LinkedList<Integer>());
         }
         // Initialize the page_frames array.
         for (int i = 0; i < nFrames; i++) {
            pageFrames[i] = -1;
         }

         /**
          * Pre-process the future hashtable before the opt algorithm is executed.
          *
          * Build a Hashtable (future) where <key> is each page’s index and
          * <value> records all that page’s locations in ascending
          * order according to the given trace file. This is done by
          * only traversing the whole trace file once initially.
          * Assign a pointer to each page at the current location.
          */
         br = new BufferedReader(new FileReader(traceFileName));
         int counter = 0;
         int line_count = 0;

         while (br.ready()) {
            line = br.readLine();
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
            // System.out.println("Parted string:");
            // for (int j = 0; j < parts.length; j++) {
            // System.out.println("Index " + j + ": " + parts[j]);
            // }
            int pageNumber = Integer.decode("0x" + parts[2].substring(0, 5));
            // System.out.println("pageNumber: " + pageNumber);
            future.get(pageNumber).add(line_count);
            line_count++;
         }
         // System.out.println("CHECKING FUTURE");
         // for (Map.Entry<Integer, LinkedList<Integer>> entry : future.entrySet()) {
         // Integer key = entry.getKey();
         // LinkedList<Integer> value = entry.getValue();
         // System.out.println("Page number: " + key);
         // System.out.println("Locations: " + value);
         // }
         /**
          * Start executing the optimal page replacement algorithm using the two
          * hashtables.
          */
         int currentFrame = 0;
         int counter2 = 0;
         br = new BufferedReader(new FileReader(traceFileName));
         while (br.ready()) {
            line = br.readLine();
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

            String[] parts = line.split(" ");
            if (!parts[1].isEmpty()) {
               accessType = parts[1].charAt(0); // Load (L), Store (S), Modify (M)
            }
            if (!parts[0].isEmpty()) {
               instructionType = parts[0].charAt(0); // I
            }

            // Convert hex to decimal page number
            int pageNumber = Integer.decode("0x" + parts[2].substring(0, 5));
            future.get(pageNumber).removeFirst();
            PageTableEntry pte = pageTable.get(pageNumber);
            pte.index = pageNumber;
            // Initialize page frames //S MEANS DIRTY BIT and VALID NEEDS TO BE SET 2 ONE
            // M IS JUST A L AND THEN A STORE
            // I AND L make invalid page valid and make it pagefault but if alrdy valid its
            // a
            // hit and set refrenced
            if (instructionType == 'I') {
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {
                     /**
                      * if (page frames is not full) swap in current page.
                      */
                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {// IDK BOUT THIS ELSE
                     /**
                      * if page_frames is full locate the page with the longest distance
                      * from current page and evict it based on the opt algorithm.
                      */
                     int longestDistance = locateLongestDistancePage(pageFrames, future);
                     PageTableEntry t_pte = pageTable.get(longestDistance);
                     /**
                      * If (swapped out page is dirty) increment the write to disk number.
                      */
                     if (t_pte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     /**
                      * evict/swap out page.
                      */
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(longestDistance, t_pte);
                  }
               } else {
                  System.out.println("HIT");
                  pte.referenced = true;
               }
            }

            if (accessType == 'S') {
               pte.dirty = true;
               pte.valid = true;
            }

            if (accessType == 'L') {
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {
                     /**
                      * if (page frames is not full) swap in current page.
                      */
                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;

                  } else {// IDK BOUT THIS ELSE
                     /**
                      * if page_frames is full locate the page with the longest distance
                      * from current page and evict it based on the opt algorithm.
                      */
                     int longestDistance = locateLongestDistancePage(pageFrames, future);
                     PageTableEntry t_pte = pageTable.get(longestDistance);
                     /**
                      * If (swapped out page is dirty) increment the write to disk number.
                      */
                     if (t_pte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     /**
                      * evict/swap out page.
                      */
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(longestDistance, t_pte);
                  }
               } else {
                  System.out.println("HIT");
                  pte.referenced = true;
               }
            }

            if (accessType == 'M') {
               if (!pte.valid) { // A load
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {
                     /**
                      * if (page frames is not full) swap in current page.
                      */
                     System.out.println("(page fault – no eviction)");
                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {// IDK BOUT THIS ELSE
                     /**
                      * if page_frames is full locate the page with the longest distance
                      * from current page and evict it based on the opt algorithm.
                      */
                     int longestDistance = locateLongestDistancePage(pageFrames, future);
                     PageTableEntry t_pte = pageTable.get(longestDistance);
                     /**
                      * If (swapped out page is dirty) increment the write to disk number.
                      */
                     if (t_pte.dirty) {
                        System.out.println("(page fault – evict dirty)");
                        totalWritesToDisk++;
                     } else {
                        System.out.println("(page fault – evict clean)");
                     }
                     /**
                      * evict/swap out page.
                      */
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(longestDistance, t_pte);
                  }
               } else {
                  System.out.println("HIT");
                  pte.referenced = true;
               }
               pte.dirty = true;
               pte.valid = true; // Followed by a store
            }
            pageTable.put(pageNumber, pte);// update pageTable in the end
            totalMemAccesses++;
         }

         printStatistics("Opt", nFrames, totalMemAccesses, totalPageFaults, totalWritesToDisk);
      } catch (Exception e) {
         e.printStackTrace();

      }

   }

   /**
    * we already know the distances of all pages in our page frames.
    * now we need to locate the page with the longest distance to evict
    * given the future hashtable and the frames.
    */
   private static int locateLongestDistancePage(int[] page_frames, Hashtable<Integer, LinkedList<Integer>> future) {
      int index = 0, max = 0;
      for (int i = 0; i < page_frames.length; i++) {
         if (future.get(page_frames[i]).isEmpty()) {
            return page_frames[i];
         } else {
            if (future.get(page_frames[i]).get(0) > max) {
               max = future.get(page_frames[i]).get(0);
               index = page_frames[i];
            }
         }
      }
      return index;
   }

   /****************************************************************************************************
    *************************** The Clock page replacement algorithm. ***********************************
    ****************************************************************************************************/
   public void clock(int nFrames, String traceFileName) {
      int totalMemAccesses = 0;
      int totalPageFaults = 0;
      int totalWritesToDisk = 0;
      int clockHandPos = 0;
      char instructionType = ' '; // Instruction (I)
      char accessType = ' '; // Load (L), Store (S), Modify (M)
      Hashtable<Integer, PageTableEntry> pageTable = new Hashtable<Integer, PageTableEntry>();
      int[] pageFrames = new int[nFrames];
      BufferedReader br = null;
      String line = " ";
      try {
         System.out.println("Initializing page table...");
         for (int i = 0; i < 1024 * 1024; i++) {
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
         br = new BufferedReader(new FileReader(traceFileName));
         while (br.ready()) {
            line = br.readLine();
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
            /**
             * swap in the page to be visited.
             */
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

            if (instructionType == 'I') {
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {
                     /**
                      * if (page frames is not full) swap in current page.
                      */
                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     /**
                      * if page_frames is full evict one page depending on the clock algorithm.
                      */
                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) {
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {
                           /**
                            * if the reference bit = 0 stop the search and evict the page.
                            */
                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {
                           /**
                            * if the reference bit = 1 clear the reference bit.
                            */
                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }
                        /**
                         * advance clock hand.
                         */
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
                     /**
                      * evict/swap out page.
                      */
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
                  pte.referenced = true;
               }
            }

            if (accessType == 'S') {
               pte.dirty = true;
               pte.valid = true;
            }

            if (instructionType == 'L') {
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {
                     /**
                      * if (page frames is not full) swap in current page.
                      */
                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     /**
                      * if page_frames is full evict one page depending on the clock algorithm.
                      */
                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) {
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {
                           /**
                            * if the reference bit = 0 stop the search and evict the page.
                            */
                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {
                           /**
                            * if the reference bit = 1 clear the reference bit.
                            */
                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }
                        /**
                         * advance clock hand.
                         */
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
                     /**
                      * evict/swap out page.
                      */
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
                  pte.referenced = true;
               }
            }

            if (instructionType == 'M') {
               if (!pte.valid) {
                  totalPageFaults++;
                  pte.valid = true;
                  if (currentFrame < nFrames) {
                     /**
                      * if (page frames is not full) swap in current page.
                      */
                     System.out.println("(page fault – no eviction)");

                     pageFrames[currentFrame] = pageNumber;
                     pte.frame = currentFrame;
                     currentFrame++;
                  } else {
                     /**
                      * if page_frames is full evict one page depending on the clock algorithm.
                      */
                     int page_num_to_evict = 0;

                     boolean found_flag = false;
                     while (found_flag == false) {
                        if (clockHandPos == pageFrames.length || clockHandPos < 0) {
                           clockHandPos = 0;
                        }
                        if (!pageTable.get(pageFrames[clockHandPos]).referenced) {
                           /**
                            * if the reference bit = 0 stop the search and evict the page.
                            */
                           page_num_to_evict = pageFrames[clockHandPos];
                           found_flag = true;
                        } else {
                           /**
                            * if the reference bit = 1 clear the reference bit.
                            */
                           pageTable.get(pageFrames[clockHandPos]).referenced = false;
                        }
                        /**
                         * advance clock hand.
                         */
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
                     /**
                      * evict/swap out page.
                      */
                     pageFrames[t_pte.frame] = pte.index;
                     pte.frame = t_pte.frame;
                     pte.valid = true;
                     t_pte.dirty = false;
                     t_pte.referenced = false;
                     t_pte.valid = false;
                     t_pte.frame = -1;
                     pageTable.put(page_num_to_evict, t_pte);
                  }
               } else {
                  System.out.println("HIT");
                  pte.referenced = true;
               }
               pte.dirty = true;
               pte.valid = true;
            }

            pageTable.put(pageNumber, pte);
            totalMemAccesses++;
         }
         /**
          * Print statistics.
          */
         printStatistics("Clock", nFrames, totalMemAccesses, totalPageFaults, totalWritesToDisk);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /****************************************************************************************************
    *********************** The Not Recently Used (NRU) page replacement algorithm.. ********************
    ****************************************************************************************************/
   public void nru(int nFrames, int refresh, String traceFileName) {
      int total_mem_accesses = 0;
      int total_page_faults = 0;
      int total_writes_to_disk = 0;
      Hashtable<Integer, PageTableEntry> pageTable = new Hashtable<Integer, PageTableEntry>();
      int[] page_frames = new int[nFrames];
      BufferedReader br = null;
      try {
         System.out.println("Initializing page table...");
         for (int i = 0; i < 1024 * 1024; i++) {
            PageTableEntry pte = new PageTableEntry();
            pageTable.put(i, pte);
         }
         // Initialize the page_frames array.
         for (int i = 0; i < nFrames; i++) {
            page_frames[i] = -1;
         }

         /**
          * Start executing the nru page replacement algorithm.
          */
         int currentFrame = 0;
         br = new BufferedReader(new FileReader(traceFileName));
         while (br.ready()) {
            /**
             * On each clock interrupt clear the reference bit, and
             * update the page table, to distinguish pages that have
             * not been referenced recently from those that have been.
             */
            if (total_mem_accesses % refresh == 0) {
               for (int i = 0; i < currentFrame; i++) {
                  PageTableEntry pte = pageTable.get(page_frames[i]);
                  pte.referenced = false;
                  pageTable.put(pte.index, pte);
               }
            }
            /**
             * swap in the page to be visited.
             */
            String[] line_arr = br.readLine().split(" ");
            int page_number = Integer.decode("0x" + line_arr[0].substring(0, 5));
            PageTableEntry pte = pageTable.get(page_number);
            pte.index = page_number;
            pte.referenced = true;
            if (line_arr[1].equals("W")) {
               pte.dirty = true;
            }
            if (pte.valid) {
               /**
                * if not page fault, continue.
                */
               System.out.println(line_arr[0] + " (hit)");
            } else {
               /**
                * if page fault, increment total page fault counter.
                */
               total_page_faults++;
               if (currentFrame < nFrames) {
                  /**
                   * if (page frames is not full) swap in current page
                   */
                  System.out.println(line_arr[0] + " (page fault – no eviction)");
                  page_frames[currentFrame] = page_number;
                  pte.frame = currentFrame;
                  pte.valid = true;
                  currentFrame++;
               } else {
                  /**
                   * if page_frames is full evict one page depending on the nru algorithm.
                   */
                  PageTableEntry page_to_evict = null;
                  int done_flag = 0;

                  /**
                   * when a page fault occurs, the OS inspects all the pages
                   * and divides them into 4 categories based on the current
                   * reference and dirty bits.
                   */
                  while (done_flag == 0) {
                     for (int p_frame = 0; p_frame < page_frames.length; p_frame++) {

                        PageTableEntry temp_pte = pageTable.get(page_frames[p_frame]);

                        if (!temp_pte.referenced && !temp_pte.dirty && temp_pte.valid) {
                           /** GATEGORY 0: not referenced, not modified **/
                           pte.frame = temp_pte.frame;
                           if (temp_pte.dirty) {
                              System.out.println(line_arr[0] + " (page fault – evict dirty)");
                              total_writes_to_disk++;
                           } else {
                              System.out.println(line_arr[0] + " (page fault – evict clean)");
                           }
                           page_frames[pte.frame] = pte.index;
                           temp_pte.valid = false;
                           temp_pte.dirty = false;
                           temp_pte.referenced = false;
                           temp_pte.frame = -1;
                           pageTable.put(temp_pte.index, temp_pte);
                           pte.valid = true;
                           pageTable.put(pte.index, pte);
                           done_flag = 1;
                           break;
                        } else {
                           if (!temp_pte.referenced && temp_pte.dirty && temp_pte.valid) {
                              /** GATEGORY 1: not referenced, modified **/
                              page_to_evict = new PageTableEntry(temp_pte);
                              continue;
                           } else {
                              if (temp_pte.referenced && !temp_pte.dirty && temp_pte.valid && page_to_evict == null) {
                                 /** GATEGORY 2: referenced, not modified **/
                                 page_to_evict = new PageTableEntry(temp_pte);
                                 continue;
                              } else {
                                 if (temp_pte.referenced && temp_pte.dirty && temp_pte.valid && page_to_evict == null) {
                                    /** GATEGORY 3: referenced, modified **/
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
                        System.out.println(line_arr[0] + " (page fault – evict dirty)");
                        total_writes_to_disk++;
                     } else {
                        System.out.println(line_arr[0] + " (page fault – evict clean)");
                     }
                     /**
                      * evict/swap out page.
                      */
                     page_frames[pte.frame] = pte.index;
                     page_to_evict.valid = false;
                     page_to_evict.dirty = false;
                     page_to_evict.frame = -1;
                     page_to_evict.referenced = false;
                     pageTable.put(page_to_evict.index, page_to_evict);
                     pte.valid = true;
                     pageTable.put(pte.index, pte);
                     done_flag = 1;
                  }
               }
            }
            /**
             * update the page table and increment total memory access count.
             */
            pageTable.put(page_number, pte);
            total_mem_accesses++;
         }

         /**
          * Print statistics.
          */
         printStatistics("NRU", nFrames, total_mem_accesses, total_page_faults, total_writes_to_disk);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * Print statistics.
    */
   private static void printStatistics(String alg, int nFrames, int total_mem_accesses, int total_page_faults,
         int total_writes_to_disk) {
      System.out.println("Algorithm: " + alg);
      System.out.println("Number of frames:\t" + nFrames);
      System.out.println("Total memory accesses:\t" + total_mem_accesses);
      System.out.println("Total page faults:\t" + total_page_faults);
      System.out.println("Total writes to disk:\t" + total_writes_to_disk);
   }
}