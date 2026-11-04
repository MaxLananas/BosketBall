package org.jctools.queues.varhandle;
import org.jctools.queues.MpscChunkedArrayQueue;
public class MpscChunkedVarHandleArrayQueue<E> extends MpscChunkedArrayQueue<E> { public MpscChunkedVarHandleArrayQueue(int initialCapacity, int maxCapacity) { super(initialCapacity, maxCapacity); } }
