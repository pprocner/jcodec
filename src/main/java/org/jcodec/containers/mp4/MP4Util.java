package org.jcodec.containers.mp4;

import static org.jcodec.common.NIOUtils.readableFileChannel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.common.NIOUtils;
import org.jcodec.common.SeekableByteChannel;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.BoxFactory;
import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.TrakBox;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class MP4Util {

    public static MovieBox createRefMovie(SeekableByteChannel input, String url) throws IOException {
        MovieBox movie = parseMovie(input);

        for (TrakBox trakBox : movie.getTracks()) {
            trakBox.setDataRef(url);
        }
        return movie;
    }

    public static MovieBox parseMovie(SeekableByteChannel input) throws IOException {
        for (Atom atom : getRootAtoms(input)) {
            if ("moov".equals(atom.getHeader().getFourcc())) {
                return (MovieBox) atom.parseBox(input);
            }
        }
        return null;
    }

    public static List<Atom> getRootAtoms(SeekableByteChannel input) throws IOException {
        input.position(0);
        List<Atom> result = new ArrayList<Atom>();
        long off = 0;
        Header atom;
        while (off < input.size()) {
            input.position(off);
            atom = Header.read(NIOUtils.fetchFrom(input, 16));
            if (atom == null)
                break;
            result.add(new Atom(atom, off));
            off += atom.getSize();
        }

        return result;
    }

    public static class Atom {
        private long offset;
        private Header header;

        public Atom(Header header, long offset) {
            this.header = header;
            this.offset = offset;
        }

        public long getOffset() {
            return offset;
        }

        public Header getHeader() {
            return header;
        }

        public Box parseBox(SeekableByteChannel input) throws IOException {
            input.position(offset + header.headerSize());
            return NodeBox.parseBox(NIOUtils.fetchFrom(input, (int) header.getSize()), header, BoxFactory.getDefault());
        }

        public void copy(SeekableByteChannel input, WritableByteChannel out) throws IOException {
            input.position(offset);
            NIOUtils.copy(input, out, header.getSize());
        }
    }

    public static MovieBox parseMovie(File source) throws IOException {
        SeekableByteChannel input = null;
        try {
            input = readableFileChannel(source);
            return parseMovie(input);
        } finally {
            if (input != null)
                input.close();
        }
    }

    public static MovieBox createRefMovie(File source) throws IOException {
        SeekableByteChannel input = null;
        try {
            input = readableFileChannel(source);
            return createRefMovie(input, "file://" + source.getCanonicalPath());
        } finally {
            if (input != null)
                input.close();
        }
    }

    public static void writeMovie(File f, MovieBox movie) throws IOException {
        FileChannel out = null;
        try {
            out = new FileInputStream(f).getChannel();
            writeMovie(f, movie);
        } finally {
            out.close();
        }
    }

    public static void writeMovie(SeekableByteChannel out, MovieBox movie) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(16 * 1024 * 1024);
        movie.write(buf);
        buf.flip();
        out.write(buf);
    }

    public static Box cloneBox(Box track, int approxSize) {
        ByteBuffer buf = ByteBuffer.allocate(approxSize);
        track.write(buf);
        buf.flip();
        return NodeBox.parseBox(buf, track.getHeader(), BoxFactory.getDefault());
    }
}