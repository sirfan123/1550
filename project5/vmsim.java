public class vmsim {

    public static void main(String[] args) {
       int nFrames = 0;
       String algorithm = "";
       int refresh = 0;
       String traceFileName = "";
       Simulate sim = new Simulate();
 
       for (int i=0; i<args.length; i++) {
          if (args[i].equals("-n")) {
                nFrames = Integer.parseInt(args[1]);
          } else if (args[i].equals("-a")) { algorithm = args[i+1]; }
          else if (args[i].equals("-r")) { refresh = Integer.parseInt(args[i+1]); }
          else if (i == args.length-1) { traceFileName = args[i]; }
       }
 
       if (algorithm.equals("opt")) {
          sim.opt(nFrames, traceFileName);
       } else if (algorithm.equals("clock")) {
          sim.clock(nFrames, traceFileName);
       } else if (algorithm.equals("nru")) {
          sim.nru(nFrames, refresh, traceFileName);
       } else {
          System.out.println("\n*Please enter opt|clock|nru| after -a .\n");
          System.exit(0);
       }
    }
 }