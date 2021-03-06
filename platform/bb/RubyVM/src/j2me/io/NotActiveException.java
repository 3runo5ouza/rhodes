/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2005 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package j2me.io;

public class NotActiveException extends ObjectStreamException {
    public NotActiveException(String reason) {
        super(reason);
    }

    public NotActiveException() {
        super();
    }
}