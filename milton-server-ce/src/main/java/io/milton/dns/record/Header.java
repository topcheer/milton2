/*
 * Copied from the DnsJava project
 *
 * Copyright (c) 1998-2011, Brian Wellington.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package io.milton.dns.record;


import java.io.*;
import java.util.*;

/**
 * A DNS message header
 *
 * @author Brian Wellington
 * @see Message
 */

public class Header implements Cloneable {

    private int id;
    private int flags;
    private int[] counts;

    private static final Random random = new Random();

    /**
     * The length of a DNS Header in wire format.
     */
    public static final int LENGTH = 12;

    private void init() {
        counts = new int[4];
        flags = 0;
        id = -1;
    }

    /**
     * Create a new empty header.
     *
     * @param id The message id
     */
    public Header(int id) {
        init();
        setID(id);
    }

    /**
     * Create a new empty header with a random message id
     */
    public Header() {
        init();
    }

    /**
     * Parses a Header from a stream containing DNS wire format.
     */
    Header(DNSInput in) throws IOException {
        this(in.readU16());
        flags = in.readU16();
        for (int i = 0; i < counts.length; i++)
            counts[i] = in.readU16();
    }

    /**
     * Creates a new Header from its DNS wire format representation
     *
     * @param b A byte array containing the DNS Header.
     */
    public Header(byte[] b) throws IOException {
        this(new DNSInput(b));
    }

    void toWire(DNSOutput out) {
        out.writeU16(getID());
        out.writeU16(flags);
        for (int count : counts) out.writeU16(count);
    }

    public byte[] toWire() {
        DNSOutput out = new DNSOutput();
        toWire(out);
        return out.toByteArray();
    }

    static private boolean validFlag(int bit) {
        return (bit >= 0 && bit <= 0xF && Flags.isFlag(bit));
    }

    static private void checkFlag(int bit) {
        if (!validFlag(bit))
            throw new IllegalArgumentException("invalid flag bit " + bit);
    }

    /**
     * Sets a flag to the supplied value
     *
     * @see Flags
     */
    public void setFlag(int bit) {
        checkFlag(bit);
        // bits are indexed from left to right
        flags |= (1 << (15 - bit));
    }

    /**
     * Sets a flag to the supplied value
     *
     * @see Flags
     */
    public void unsetFlag(int bit) {
        checkFlag(bit);
        // bits are indexed from left to right
        flags &= ~(1 << (15 - bit));
    }

    /**
     * Retrieves a flag
     *
     * @see Flags
     */
    public boolean getFlag(int bit) {
        checkFlag(bit);
        // bits are indexed from left to right
        return (flags & (1 << (15 - bit))) != 0;
    }

    boolean[] getFlags() {
        boolean[] array = new boolean[16];
        for (int i = 0; i < array.length; i++)
            if (validFlag(i))
                array[i] = getFlag(i);
        return array;
    }

    /**
     * Retrieves the message ID
     */
    public int getID() {
        if (id >= 0)
            return id;
        synchronized (this) {
            if (id < 0)
                id = random.nextInt(0xffff);
            return id;
        }
    }

    /**
     * Sets the message ID
     */
    public void setID(int id) {
        if (id < 0 || id > 0xffff)
            throw new IllegalArgumentException("DNS message ID " + id +
                    " is out of range");
        this.id = id;
    }

    /**
     * Sets the message's rcode
     *
     * @see Rcode
     */
    public void setRcode(int value) {
        if (value < 0 || value > 0xF)
            throw new IllegalArgumentException("DNS Rcode " + value +
                    " is out of range");
        flags &= ~0xF;
        flags |= value;
    }

    /**
     * Retrieves the mesasge's rcode
     *
     * @see Rcode
     */
    public int getRcode() {
        return flags & 0xF;
    }

    /**
     * Sets the message's opcode
     *
     * @see Opcode
     */
    public void setOpcode(int value) {
        if (value < 0 || value > 0xF)
            throw new IllegalArgumentException("DNS Opcode " + value +
                    "is out of range");
        flags &= 0x87FF;
        flags |= (value << 11);
    }

    /**
     * Retrieves the mesasge's opcode
     *
     * @see Opcode
     */
    public int getOpcode() {
        return (flags >> 11) & 0xF;
    }

    void setCount(int field, int value) {
        if (value < 0 || value > 0xFFFF)
            throw new IllegalArgumentException("DNS section count " +
                    value + " is out of range");
        counts[field] = value;
    }

    public void incCount(int field) {
        if (counts[field] == 0xFFFF)
            throw new IllegalStateException("DNS section count cannot " +
                    "be incremented");
        counts[field]++;
    }

    public void decCount(int field) {
        if (counts[field] == 0)
            throw new IllegalStateException("DNS section count cannot " +
                    "be decremented");
        counts[field]--;
    }

    /**
     * Retrieves the record count for the given section
     *
     * @see Section
     */
    public int getCount(int field) {
        return counts[field];
    }

    /**
     * Converts the header's flags into a String
     */
    public String printFlags() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 16; i++)
            if (validFlag(i) && getFlag(i)) {
                sb.append(Flags.string(i));
                sb.append(" ");
            }
        return sb.toString();
    }

    String toStringWithRcode(int newrcode) {
        StringBuilder sb = new StringBuilder();

        sb.append(";; ->>HEADER<<- ");
        sb.append("opcode: ").append(Opcode.string(getOpcode()));
        sb.append(", status: ").append(Rcode.string(newrcode));
        sb.append(", id: ").append(getID());
        sb.append("\n");

        sb.append(";; flags: ").append(printFlags());
        sb.append("; ");
        for (int i = 0; i < 4; i++)
            sb.append(Section.string(i)).append(": ").append(getCount(i)).append(" ");
        return sb.toString();
    }

    /**
     * Converts the header into a String
     */
    public String toString() {
        return toStringWithRcode(getRcode());
    }

    /* Creates a new Header identical to the current one */
    public Object clone() {
        Header h = new Header();
        h.id = id;
        h.flags = flags;
        System.arraycopy(counts, 0, h.counts, 0, counts.length);
        return h;
    }

}
