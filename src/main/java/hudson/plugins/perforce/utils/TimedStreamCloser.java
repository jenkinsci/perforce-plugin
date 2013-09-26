/*
 * The MIT License
 *
 * Copyright 2013 rpetti.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.perforce.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.Pipe.SourceChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.time.StopWatch;

/**
 *
 * @author rpetti
 */
public class TimedStreamCloser extends Thread {
    private long seconds;
    private InputStream in;
    private long timeout;
    private boolean keepRunning = true;
    private boolean timedOut = false;
    private Object lock;
    
    public TimedStreamCloser(InputStream in, long timeout) throws IOException {
        this.in = in;
        this.timeout = timeout;
        lock = this;
    }
    
    public void reset() {
        synchronized (lock) {
            seconds = 0;
        }
    }
    
    public void close() {
        keepRunning = false;
        try {
            in.close();
        } catch (IOException ex) {
            Logger.getLogger(TimedStreamCloser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        if (timeout < 0) return; //no timeout, so do nothing
        this.reset();
        
        while(keepRunning) {
            if(seconds > timeout){
                timedOut = true;
                close();
                break;
            }

            try {
                Thread.sleep(1000);
                seconds++;
            } catch (InterruptedException ex) {
                close();
                break;
            }
        }
    }
    
    public boolean timedOut() {
        return timedOut;
    }

}
