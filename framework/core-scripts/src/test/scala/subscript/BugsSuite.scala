package subscript

import scala.util._

import subscript.language
import subscript.Predef._

import org.scalatest._

class BugsSuite extends FlatSpec with Matchers
                                  with CommonHelpers {

  "#26" should "work" in {
    var i = 0
    script foo = {!i += 1!};
                 {!i += 1!}

    runScript(foo)
    
    i shouldBe 2
  }

  "#28" should "work" in {
    script s = [^1^^1 ^2^^2]^^
    runScript(s).$ shouldBe Success(List((1, 2)))
  }

  "#29" should "be possible to call nice while with prefixed simple expression" in {
    var i = 0
    script s = var flag = false
               [while !flag {!i += 1!} {!if (i >= 3) flag = true!}]
  
    runScript(s)
    i shouldBe 3
  }

  it should "be possible to use prefixed expressions in nice script calls" in {
    script..
      a(x: Boolean) = if x then ^1 else ^2
      b = a: !true

    runScript(b).$ shouldBe Success(2)
  }

  it should "be possible to write if guards for actual constrained parameters" in {
    script..
      f(??x: Int) = let x = 3
      live = 
        var x = 0
        f: ?x ?if (x > (0))
        ^x

    runScript(live).$ shouldBe Success(3)
  }

  it should "be possible to use Smalltalk-like script calls" in {
    script..
      fooBar(x: Int, y: Int) = ^(x + y)
      live = foo: 1, bar: 2

    runScript(live).$ shouldBe Success(3)
  }

  it should "be possible to use nested nice calls" in {
    def square(x: Int) = x * x

    script..
      fooBar(x: Int, y: Int) = ^(x + y)
      live = foo: square: square: 2, bar: square: square: square: square: 2

    runScript(live).$ shouldBe Success(65552)
  }

  "#39" should "work" in {
    def ifZeroThenDoElseDo(a: Int, b: Int, c: Int) = if (a == 0) b else c

    script s2(i: Int) = ^ifZero: i , thenDo:  1, elseDo:  2

    runScript(s2(0)).$ shouldBe Success(1)
    runScript(s2(1)).$ shouldBe Success(2)
  }


}
