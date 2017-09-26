package ca.mcgill.science.tepid.server.gs;

import ca.mcgill.science.tepid.server.util.DuplicatedOutputStream;
import ca.mcgill.science.tepid.server.util.StreamDuplicator;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GS {
    private static final String gsBin = System.getProperty("os.name").startsWith("Windows") ? "C:/Program Files/gs/gs9.20/bin/gswin64c.exe" : "gs";

    public static Process run(String... args) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add(gsBin);
        allArgs.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(allArgs);
        try {
            return pb.start();
        } catch (IOException e) {
            throw new GSException("Could not invoke GS", e);
        }
    }


    public static File ps2pdf(final InputStream is) {
        File f;
        try {
            f = File.createTempFile("tepid", ".pdf");
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
        Process p = GS.run("-dNOPAUSE", "-dBATCH", "-dSAFER", "-sDEVICE=pdfwrite", "-dQUIET", "-q", "-sstdout=%stderr",
                "-sOutputFile=" + f.getAbsolutePath(), "-c", ".setpdfwrite", "-");
        copyData(p, is);
        return f;
    }

    private static void copyData(Process p, InputStream is) {
        final OutputStream os = p.getOutputStream();
        new Thread("GS copy data into process") {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4092];
                    int bytes;
                    while ((bytes = is.read(buf)) > 0) {
                        os.write(buf, 0, bytes);
                    }
                    os.flush();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    //todo check unused method. Seems to be useless? - Allan
    public static List<InkCoverage> inkCoverage(final InputStream f) {
        Process p = GS.run("-sOutputFile=%stdout%", "-dBATCH", "-dNOPAUSE", "-dQUIET", "-q", "-sDEVICE=inkcov", "-");
        copyData(p, f);
        return inkCoverage(p);
    }

    private static final Pattern floats = Pattern.compile("([0-9]+\\.[0-9]+)\\s+([0-9]+\\.[0-9]+)\\s+([0-9]+\\.[0-9]+)\\s+([0-9]+\\.[0-9]+)");

    private static List<InkCoverage> inkCoverage(Process p) {
        List<InkCoverage> out = new ArrayList<>();
        try {
            BufferedReader gs = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = gs.readLine()) != null) {
                line = line.trim();
                Matcher vals = floats.matcher(line);
                if (!vals.find()) continue;
                try {
                    out.add(new InkCoverage(Float.parseFloat(vals.group(1)), Float.parseFloat(vals.group(2)), Float.parseFloat(vals.group(3)), Float.parseFloat(vals.group(4))));
                } catch (NumberFormatException e) {
                    throw new GSException(line);
                }
            }
            gs.close();
        } catch (IOException e) {
            throw new RuntimeException("Ghostscript failed", e);
        }
        return out;
    }

    public static List<InkCoverage> inkCoverage(final File f) {
        Process p = GS.run("-sOutputFile=%stdout%", "-dBATCH", "-dNOPAUSE", "-dQUIET", "-q", "-sDEVICE=inkcov", f.getAbsolutePath());
        return inkCoverage(p);
    }

    public static class InkCoverage {
        public final float c, m, y, k;
        public final boolean monochrome;

        public InkCoverage(float c, float m, float y, float k) {
            this.c = c;
            this.m = m;
            this.y = y;
            this.k = k;
            this.monochrome = c == m && c == y;
        }

        public InkCoverage(String c, String m, String y, String k) {
            this(Float.parseFloat(c), Float.parseFloat(m), Float.parseFloat(y), Float.parseFloat(k));
        }

        @Override
        public String toString() {
            return "Page [c=" + c + ", m=" + m + ", y=" + y + ", k=" + k + ", monochrome=" + monochrome + "]";
        }

    }

    public static class GSRenderCallback {
        private BlockingQueue<InputStream> out = new LinkedBlockingQueue<>();
        protected boolean done;

        protected void addStream(InputStream is) {
            out.add(is);
        }

        public InputStream nextPage() {
            try {
                return out.take();
            } catch (InterruptedException ignored) {
            }
            return null;
        }

        public boolean isDone() {
            return done;
        }

        public boolean hasMore() {
            return !out.isEmpty();
        }
    }

    public static GSRenderCallback render(InputStream f, int res) {
        return render(f, res, "png16m");
    }

    public static GSRenderCallback render(final InputStream f, final int res, final String format) {
        final GSRenderCallback out = new GSRenderCallback();
        new Thread("GS rendering") {
            @Override
            public void run() {
                try {
                    Process p = GS.run("-dNOPAUSE", "-dBATCH", "-dSAFER", "-sDEVICE=" + format,
                            "-dGraphicsAlphaBits=4", "-dTextAlphaBits=4", "-dDownScaleFactor=2",
                            "-sOutputFile=%stdout%", "-r" + res * 2, "-");
                    copyData(p, f);
                    InputStream is = p.getInputStream();
                    byte[] buf = new byte[2048];
                    int bytesRead, magic = 0, iend = 0;
                    boolean write = false;
                    DuplicatedOutputStream pos = null;
                    while ((bytesRead = is.read(buf)) > 0) {
                        int start = 0, length = -1;
                        for (int i = 0; i < bytesRead; i++) {
                            byte b = buf[i];
                            if (b == 0x89) magic = 0; //todo check always false statement
                            else if (magic == 0 && b == 'P') magic = 1;
                            else if (magic == 1 && b == 'N') magic = 2;
                            else if (magic == 2 && b == 'G') {
                                magic = 0;
                                start = i - 3;
                                write = true;
                                pos = StreamDuplicator.makePipe();
                                out.addStream(pos.getInputStream());
                            } else {
                                magic = 0;
                            }
                            if (b == 'I') iend = 0;
                            else if (iend == 0 && b == 'E') iend = 1;
                            else if (iend == 1 && b == 'N') iend = 2;
                            else if (iend == 2 && b == 'D') {
                                iend = 0;
                                length = i + 5 > bytesRead ? bytesRead : i + 5;
                            } else {
                                iend = 0;
                            }
                        }
                        if (length != -1 && pos != null) {
                            pos.write(buf, start, length - start);
                            write = false;
                            pos.close();
                            pos = null;
                        } else if (start != 0) {
                            pos.write(buf, start, bytesRead - start);
                        } else if (write) {
                            pos.write(buf, 0, bytesRead);
                        } else {
                            //					System.out.println(new String(buf));
                        }
                    }
                    out.done = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return out;
    }
}
