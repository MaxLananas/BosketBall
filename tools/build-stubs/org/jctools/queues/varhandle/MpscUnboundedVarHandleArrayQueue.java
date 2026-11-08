package org.jctools.queues.varhandle;
import org.jctools.queues.MpscUnboundedArrayQueue;
public class MpscUnboundedVarHandleArrayQueue<E> extends MpscUnboundedArrayQueue<E> { public MpscUnboundedVarHandleArrayQueue(int chunkSize) { super(chunkSize); } }
