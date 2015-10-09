package scalaParser.subscript.parser

import language.implicitConversions
import org.parboiled2._
import scalaParser._
import scalaParser.syntax._

import scalaParser.subscript.ast.Ast


trait Core {this: SubScript with Exprs =>
  def wspStrR0(s: String): R0 = rule( WLR0 ~ str(s))
  def wspChR0 (s: Char  ): R0 = rule( WLR0 ~ ch (s))

  type R[T] = Rule1[T]

  def `script` = KeyWordOperators.W("script")
  def `let`    = KeyWordOperators.W("let")

  def `..`     = KeyWordOperators.O("..")
  def `+=`     = KeyWordOperators.O("+=")

  def `|`    = KeyWordOperators.O("|"   )
  def `||`   = KeyWordOperators.O("||"  )
  def `&`    = KeyWordOperators.O("&"   )
  def `&&`   = KeyWordOperators.O("&&"  )
  def `==`   = KeyWordOperators.O("=="  )
  def plus   = KeyWordOperators.O("+"   )
  def `/`    = KeyWordOperators.O("/"   )
  def `%`    = KeyWordOperators.O("%"   )
  def `%/%/` = KeyWordOperators.O("%/%/")
  def `%/`   = KeyWordOperators.O("%/"  )
  def `%%`   = KeyWordOperators.O("%%"  )
  def `~~`   = KeyWordOperators.O("~~"  )
  def `+~/~` = KeyWordOperators.O("+~/~")

  def `<<`   = KeyWordOperators.O("<<")
  def `>>`   = KeyWordOperators.O(">>")
  def `==>`  = KeyWordOperators.O("==>")

  def SSKeyword  = rule (`then` | `script` | `let`)
  def SSOperator = rule (
    `|`  | `||`   | `&`  | `&&` | `==`  | plus | `/` | `%` | `%/%/` | `%/` | `%%` | capture(Basic.Newline)
  | `~~` | `+~/~` | `>>` | `==>`
  )

  def SSOperatorOrKeyword = rule (SSKeyword | SSOperator)

  def IdentedNewLine(col: Int): R0 = {
    def ValidCol: R0 = {
      val currentCol = Position(cursor, input).column
      if (currentCol > col) MATCH else MISMATCH0
    }
    
    rule { WSR0 ~ Basic.Newline ~ WLR0 ~ ValidCol }
  }

  /**
   * Consumes all the white spaces (ws) before the rule r silently.
   */
  def Spaces[T](r: () => R[T], ws: () => R0 = () => WSR0): R[T] = rule { ws() ~ r() }

  def IdS       = Spaces(() => Id)
  def StableIdS = Spaces(() => StableId)
}

trait HighPriorityRulesConversions extends RuleDSLBasics {this: SubScript with Exprs =>
  implicit def wspStrR1(s: String): R1 = rule( capture(wspStrR0(s)) )
  implicit def wspChR1 (s: Char  ): R1 = rule( capture(wspChR0 (s)) )
}