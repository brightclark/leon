package withOrb

import leon._
import mem._
import higherorder._
import lang._
import annotation._
import collection._
import instrumentation._
import invariant._

object RealTimeQueue {

  sealed abstract class Stream[T] {
    @inline
    def isEmpty: Boolean = {
      this == SNil[T]()
    }

    @inline
    def isCons: Boolean = !isEmpty

    /**
     * This function does not use a memoized version and hence is side-effect free
     */
    def size: BigInt = {
      this match {
        case SNil()      => BigInt(0)
        case c@SCons(_, _) => 1 + (c.tail*).size
      }
    } ensuring (_ >= 0)

    @memoize
    def tail : Stream[T] = {
      require(isCons)
      this match {
        case SCons(x, tailFun) => tailFun()
      }
    }
  }
  case class SCons[T](x: T, tailFun: () => Stream[T]) extends Stream[T]
  case class SNil[T]() extends Stream[T]

  /*@inline
  def evaluated[T](l: () => Stream[T]): Boolean = {
    l fmatch[() => Stream[T], List[T], () => Stream[T], Boolean] {
      case (f, r, a) if l.is(() => rotate(f,r,a)) =>
        rotate(f,r,a).cached
      case _ => true
    }
  }*/

  def isConcrete[T](l: Stream[T]): Boolean = {
    l match {
      case c@SCons(_, _) =>
        c.tail.cached && isConcrete(c.tail*)
      case _ => true
    }
  }

  @invisibleBody
  @invstate // doesn't change state
  def rotate[T](f: Stream[T], r: List[T], a: Stream[T]): Stream[T] = {
    require(r.size == f.size + 1 && isConcrete(f))
    (f, r) match {
      case (SNil(), Cons(y, _)) => //in this case 'y' is the only element in 'r'
        SCons[T](y, lift(a))
      case (c@SCons(x, _), Cons(y, r1)) =>
        val newa = SCons[T](y, lift(a))
        val ftail = c.tail
        val rot = () => rotate(ftail, r1, newa)
        SCons[T](x, rot)
    }
  } ensuring (res => res.size == f.size + r.size + a.size && res.isCons)
//      /&& time <= 30)

  /**
   * Returns the first element of the stream whose tail is not evaluated.
   */
  def firstUnevaluated[T](l: Stream[T]): Stream[T] = {
    l match {
      case c @ SCons(_, _) =>
        if (c.tail.cached)
          firstUnevaluated(c.tail*)
        else l
      case _           => l
    }
  } ensuring (res => (!res.isEmpty || isConcrete(l)) && //if there are no lazy closures then the stream is concrete
    (res match {
      case c@SCons(_, _) =>
        firstUnevaluated(l) == firstUnevaluated(c.tail) // after evaluating the firstUnevaluated closure in 'l' we can access the next unevaluated closure
      case _ => true
    }))

  case class Queue[T](f: Stream[T], r: List[T], s: Stream[T]) {
    @inline
    def isEmpty = f.isEmpty

    //@inline
    def valid = {
      (firstUnevaluated(f) == firstUnevaluated(s)) &&
        s.size == f.size - r.size //invariant: |s| = |f| - |r|
    }
  }

  @inline
  def createQ[T](f: Stream[T], r: List[T], s: Stream[T]) = {
    s match {
      case c@SCons(_, _) => Queue(f, r, c.tail) // force the schedule once
      case SNil() =>
        val rotres = rotate(f, r, SNil[T]())
        Queue(rotres, Nil(), rotres)
    }
  }

  def empty[T] = {
    val a: Stream[T] = SNil()
    Queue(a, Nil(), a)
  }

  def head[T](q: Queue[T]): T = {
    require(!q.isEmpty && q.valid)
    q.f match {
      case SCons(x, _) => x
    }
  } //ensuring (res => res.valid && time <= ?)

  def enqueue[T](x: T, q: Queue[T]): Queue[T] = {
    require(q.valid)
    val r = Cons(x, q.r)
    q.s match {
      case c @ SCons(_, _) => Queue(q.f, r, c.tail) // force the schedule once
      case SNil() =>
        val rotres = rotate(q.f, r, SNil[T]())
        Queue(rotres, Nil(), rotres)
    }
  } ensuring { res =>
    /*val in = inState[Stream[T]]
    val out = outState[Stream[T]]
    funeMonotone(q.f, q.s, in, out)*/
    q.s match {
      case SCons(_, _) => firstUnevaluated(q.s) == firstUnevaluated(res.s)
      case _           => true
    }
  }
    //res.s.size == res.f.size - res.r.size) // && time <= ?)

  def dequeue[T](q: Queue[T]): Queue[T] = {
    require(!q.isEmpty && q.valid)
    q.f match {
      case c@SCons(x, _) =>
        createQ(c.tail, q.r, q.s)
    }
  } ensuring (res => res.valid) // && time <= ?)

   // Properties of `firstUneval`. We use `fune` as a shorthand for `firstUneval`
  /**
   * st1.subsetOf(st2) ==> fune(l, st2) == fune(fune(l, st1), st2)
   */
  /*@traceInduct
  def funeCompose[T](l1: Stream[T], st1: Set[Mem[Stream[T]]], st2: Set[Mem[Stream[T]]]): Boolean = {
    require(st1.subsetOf(st2))
    // property
    (firstUnevaluated(l1) withState st2) == (firstUnevaluated(firstUnevaluated(l1) withState st1) withState st2)
  } holds*/

  def funeMonotone[T](l1: Stream[T], l2: Stream[T], st1: Set[Mem[Stream[T]]], st2: Set[Mem[Stream[T]]]): Boolean = {
    require((firstUnevaluated(l1) withState st1) == (firstUnevaluated(l2) withState st1) &&
        st1.subsetOf(st2))
     //funeCompose(l1, st1, st2) && // lemma instantiations
     //funeCompose(l2, st1, st2) &&
     // induction scheme
    (l1 match {
      case c @ SCons(_, _) =>
          funeMonotone(c.tail* , l2, st1, st2)
      case _           => true
    }) &&
      (firstUnevaluated(l1) withState st2) == (firstUnevaluated(l2) withState st2) // property
  } holds

  //@ignore
  /*def main(args: Array[String]) {
    //import eagerEval.AmortizedQueue
    import scala.util.Random
    import scala.math.BigInt
    import stats._
    import collection._

    println("Running RTQ test...")
    val ops = 10000000
    val rand = Random
    // initialize to a queue with one element (required to satisfy preconditions of dequeue and front)
    var rtq = empty[BigInt]
    //var amq = AmortizedQueue.Queue(AmortizedQueue.Nil(), AmortizedQueue.Nil())
    var totalTime1 = 0L
    var totalTime2 = 0L
    println(s"Testing amortized emphemeral behavior on $ops operations...")
    for (i <- 0 until ops) {
      if (!rtq.isEmpty) {
        val h1 = head(rtq)
        //val h2 = amq.head
        //assert(h1 == h2, s"Eager head: $h2 Lazy head: $h1")
      }
      rand.nextInt(2) match {
        case x if x == 0 => //enqueue
          //          /if(i%100000 == 0) println("Enqueue..")
          rtq = timed { enqueue(BigInt(i), rtq) } { totalTime1 += _ }
          //amq = timed { amq.enqueue(BigInt(i)) } { totalTime2 += _ }
        case x if x == 1 => //dequeue
          if (!rtq.isEmpty) {
            //if(i%100000 == 0) println("Dequeue..")
            rtq = timed { dequeue(rtq) } { totalTime1 += _ }
            //amq = timed { amq.dequeue } { totalTime2 += _ }
          }
      }
    }
    println(s"Ephemeral Amortized Time - Eager: ${totalTime2 / 1000.0}s Lazy: ${totalTime1 / 1000.0}s") // this should be linear in length for both cases
    // now, test worst-case behavior (in persitent mode if necessary)
    val length = (1 << 22) - 2 // a number of the form: 2^{n-2}
    // reset the queues
    rtq = empty[BigInt]
    //amq = AmortizedQueue.Queue(AmortizedQueue.Nil(), AmortizedQueue.Nil())
    // enqueue length elements
    for (i <- 0 until length) {
      rtq = enqueue(BigInt(0), rtq)
      //amq = amq.enqueue(BigInt(0))
    }
    //println(s"Amortized queue size: ${amq.front.size}, ${amq.rear.size}")
    //dequeue 1 element from both queues
    //timed { amq.dequeue } { t => println(s"Time to dequeue one element from Amortized Queue in the worst case: ${t / 1000.0}s") }
    timed { dequeue(rtq) } { t => println(s"Time to dequeue one element from RTQ in the worst case: ${t / 1000.0}s") }
  }*/
}
