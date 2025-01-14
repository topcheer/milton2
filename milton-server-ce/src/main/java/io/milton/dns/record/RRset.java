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

import io.milton.dns.Name;

import java.io.Serializable;
import java.util.*;

/**
 * A set of Records with the same name, type, and class.  Also included
 * are all RRSIG records signing the data records.
 * @see Record
 * @see RRSIGRecord 
 *
 * @author Brian Wellington
 */

public class RRset implements Serializable {

private static final long serialVersionUID = -3270249290171239695L;

/*
 * rrs contains both normal and RRSIG records, with the RRSIG records
 * at the end.
 */
private final List rrs;
private short nsigs;
private short position;

/** Creates an empty RRset */
public
RRset() {
	rrs = new ArrayList(1);
	nsigs = 0;
	position = 0;
}

/** Creates an RRset and sets its contents to the specified record */
public
RRset(Record record) {
	this();
	safeAddRR(record);
}

/** Creates an RRset with the contents of an existing RRset */
public
RRset(RRset rrset) {
	synchronized (rrset) {
		rrs = (List) ((ArrayList)rrset.rrs).clone();
		nsigs = rrset.nsigs;
		position = rrset.position;
	}
}

private void
safeAddRR(Record r) {
	if (!(r instanceof RRSIGRecord)) {
		if (nsigs == 0)
			rrs.add(r);
		else
			rrs.add(rrs.size() - nsigs, r);
	} else {
		rrs.add(r);
		nsigs++;
	}
}

/** Adds a Record to an RRset */
public synchronized void
addRR(Record r) {
	if (rrs.isEmpty()) {
		safeAddRR(r);
		return;
	}
	Record first = first();
	if (!r.sameRRset(first))
		throw new IllegalArgumentException("record does not match " +
						   "rrset");

	if (r.getTTL() != first.getTTL()) {
		if (r.getTTL() > first.getTTL()) {
			r = r.cloneRecord();
			r.setTTL(first.getTTL());
		} else {
			for (int i = 0; i < rrs.size(); i++) {
				Record tmp = (Record) rrs.get(i);
				tmp = tmp.cloneRecord();
				tmp.setTTL(r.getTTL());
				rrs.set(i, tmp);
			}
		}
	}

	if (!rrs.contains(r))
		safeAddRR(r);
}

/** Deletes a Record from an RRset */
public synchronized void
deleteRR(Record r) {
	if (rrs.remove(r) && (r instanceof RRSIGRecord))
		nsigs--;
}

/** Deletes all Records from an RRset */
public synchronized void
clear() {
	rrs.clear();
	position = 0;
	nsigs = 0;
}

private synchronized Iterator
iterator(boolean data, boolean cycle) {
	int size, start, total;

	total = rrs.size();

	if (data)
		size = total - nsigs;
	else
		size = nsigs;
	if (size == 0)
		return Collections.EMPTY_LIST.iterator();

	if (data) {
		if (!cycle)
			start = 0;
		else {
			if (position >= size)
				position = 0;
			start = position++;
		}
	} else {
		start = total - nsigs;
	}

	List list = new ArrayList(size);
	if (data) {
		list.addAll(rrs.subList(start, size));
		if (start != 0)
			list.addAll(rrs.subList(0, start));
	} else {
		list.addAll(rrs.subList(start, total));
	}

	return list.iterator();
}

/**
 * Returns an Iterator listing all (data) records.
 * @param cycle If true, cycle through the records so that each Iterator will
 * start with a different record.
 */
public synchronized Iterator
rrs(boolean cycle) {
	return iterator(true, cycle);
}

/**
 * Returns an Iterator listing all (data) records.  This cycles through
 * the records, so each Iterator will start with a different record.
 */
public synchronized Iterator
rrs() {
	return iterator(true, true);
}

/** Returns an Iterator listing all signature records */
public synchronized Iterator
sigs() {
	return iterator(false, false);
}

/** Returns the number of (data) records */
public synchronized int
size() {
	return rrs.size() - nsigs;
}

/**
 * Returns the name of the records
 * @see Name
 */
public Name
getName() {
	return first().getName();
}

/**
 * Returns the type of the records
 * @see Type
 */
public int
getType() {
	return first().getRRsetType();
}

/**
 * Returns the class of the records
 * @see DClass
 */
public int
getDClass() {
	return first().getDClass();
}

/** Returns the ttl of the records */
public synchronized long
getTTL() {
	return first().getTTL();
}

/**
 * Returns the first record
 * @throws IllegalStateException if the rrset is empty
 */
public synchronized Record
first() {
	if (rrs.isEmpty())
		throw new IllegalStateException("rrset is empty");
	return (Record) rrs.get(0);
}

private String
iteratorToString(Iterator it) {
	StringBuilder sb = new StringBuilder();
	while (it.hasNext()) {
		Record rr = (Record) it.next();
		sb.append("[");
		sb.append(rr.rdataToString());
		sb.append("]");
		if (it.hasNext())
			sb.append(" ");
	}
	return sb.toString();
}

/** Converts the RRset to a String */
public String
toString() {
	if (rrs == null)
		return ("{empty}");
	StringBuilder sb = new StringBuilder();
	sb.append("{ ");
	sb.append(getName()).append(" ");
	sb.append(getTTL()).append(" ");
	sb.append(DClass.string(getDClass())).append(" ");
	sb.append(Type.string(getType())).append(" ");
	sb.append(iteratorToString(iterator(true, false)));
	if (nsigs > 0) {
		sb.append(" sigs: ");
		sb.append(iteratorToString(iterator(false, false)));
	}
	sb.append(" }");
	return sb.toString();
}

}
