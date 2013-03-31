package unfiltered.util.control

/** pre 2.10 shim for 2.10 scala.util.control.NonFatal.
 *  this is mostly for use with handling catch exceptions where
 *  you would not normally want to swallow fatals. 2.10 scalac
 *  will actually bark at matching an unqualified _ by default
 */
object NonFatal {
  /**
    * Returns true if the provided `Throwable` is to be considered non-fatal, or false if it is to be considered fatal
    */
   def apply(t: Throwable): Boolean = t match {
     case _: StackOverflowError => true // StackOverflowError ok even though it is a VirtualMachineError
     // VirtualMachineError includes OutOfMemoryError and other fatal errors
     case _: VirtualMachineError | _: ThreadDeath | _: InterruptedException | _: LinkageError/* 2.10 | _: ControlThrowable*/ => false
     case _ => true
   }
  /**
   * Returns Some(t) if NonFatal(t) == true, otherwise None
   */
   def unapply(t: Throwable): Option[Throwable] = if (apply(t)) Some(t) else None
}


