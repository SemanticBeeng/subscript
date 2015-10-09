package scalaParser.subscript.parser

import language.implicitConversions
import org.parboiled2._
import scalaParser._
import scalaParser.syntax._

import shapeless._

import scalaParser.subscript.ast.Ast


trait Terms {this: Operators with SubScript with Exprs =>

  def ScriptTerm: R[Ast.Literal] =
    rule {IdS ~> Ast.Literal}

  def ValueExpr: R[Ast.Literal] = ScriptTerm

  def SimpleValueExpr: R[Ast.Literal] = rule { WithNormalInScript {() => StatCtx.Expr} ~> Ast.Literal}
  
  def ScriptCall: R[Ast.ScriptCall] = rule {!SSOperatorOrKeyword ~ (
    ScriptCallNice
  | ScriptCallOrdinary
  )}

  def ScriptCallOrdinary: R[Ast.ScriptCall] =
    rule {(StableIdS ~ ((!WLOneOrMoreR0 ~ TypeArgs).? ~> ExtractOpt) ~ ((!WLOneOrMoreR0 ~ ArgList).? ~> ExtractOpt) ~> Concat3 | Literal) ~> Ast.Literal ~> Ast.ScriptCall}

  def ScriptCallNice: R[Ast.ScriptCall] = {
    def Trans1: Seq[String] => String = exprs => s"(${exprs.mkString(", ")})"
    def ExprsStat: R1 = rule { (WSR0 ~ StatCtx.SimpleExpr).+(ch(',')) ~> Trans1 }

    rule {StableIdS ~ wspChR0(':') ~ WithNiceScriptCall {() => ExprsStat} ~> Concat ~> Ast.Literal ~> Ast.ScriptCall}
  }



  // Code fragments
  def CodeFragment: R[Ast.CodeFragment] = {
    def Body = rule (
        EventhandlingLoop
      | Threaded
      | Unsure
      | Normal
      | Eventhandling
      | Tiny
    )

    WithNormalInScript {() => Body}
  }

  def CodeFragmentMeta(symbolStart: String, symbolEnd: String): R1 = {
    rule {wspStrR0(symbolStart) ~ Block ~ wspStrR0(symbolEnd)}
  }

  def CodeFragmentSimpleMeta[T <: Ast.CodeFragment](symbol: String, generator: String => T): R[T] =
    rule {CodeFragmentMeta(s"{$symbol", s"$symbol}") ~> generator}

  def Normal           : R[Ast.Normal           ] = CodeFragmentSimpleMeta("!"   , Ast.Normal          )
  def Threaded         : R[Ast.Threaded         ] = CodeFragmentSimpleMeta("*"  , Ast.Threaded         )
  def Unsure           : R[Ast.Unsure           ] = CodeFragmentSimpleMeta("?"  , Ast.Unsure           )
  def Tiny             : R[Ast.Tiny             ] = CodeFragmentSimpleMeta(":"  , Ast.Tiny              )
  def Eventhandling    : R[Ast.Eventhandling    ] = CodeFragmentSimpleMeta("."  , Ast.Eventhandling    )
  def EventhandlingLoop: R[Ast.EventhandlingLoop] = CodeFragmentSimpleMeta("...", Ast.EventhandlingLoop)

  // Declarations
  def Declaration: R[Ast.Declaration] = rule {VarDecl | ValDecl}

  def StandardDecl[T <: Ast.Declaration](keyword: () => Rule1[String], generator: (String, Option[String], Ast.Node) => T) = {
    def Trans1: (String, String, Option[String], String, Ast.Literal) => T =
      (_, id, tpe, _, expr) => generator(id, tpe, expr)

    rule {keyword() ~ IdS ~ (`:` ~ Spaces(() => Type) ~> SecondStr).? ~ `=` ~ WSR0 ~ SimpleValueExpr ~> Trans1}
  }

  def VarDecl: R[Ast.VarDecl] = StandardDecl(() => `var`, Ast.VarDecl)
  def ValDecl: R[Ast.ValDecl] = StandardDecl(() => `val`, Ast.ValDecl)


  // Special leafs
  def Special: R[Ast.Term] = rule (
    Delta
  | Epsilon
  | Neutral
  | WhileLeaf
  | Let
  | Loop
  | OptionalBreakLoop
  | OptionalBreak
  | Break
  )

  def SpecialConstant(symbol: String, counterpart: Ast.SpecialConstant): R[Ast.SpecialConstant] =
    rule (wspStrR1(symbol) ~> {_: String => counterpart})

  def Delta             = SpecialConstant("[-]"  , Ast.Delta            )
  def Epsilon           = SpecialConstant("[+]"  , Ast.Epsilon          )
  def Neutral           = SpecialConstant("[+-]" , Ast.Neutral          )
  def Loop              = SpecialConstant("..."  , Ast.Loop             )
  def OptionalBreakLoop = SpecialConstant(".."   , Ast.OptionalBreakLoop)
  def OptionalBreak     = SpecialConstant("."    , Ast.OptionalBreak    )
  def Break             = SpecialConstant("break", Ast.Break            )


  def WhileLeaf: R[Ast.While] = {
    def Trans1: (String, String, String, String) => Ast.While = (_, _, condition, _) => Ast.While(condition)
    def Trans2: (String, String)                 => Ast.While = (_,    condition   ) => Ast.While(condition)
  
    def Standard: R[Ast.While] = rule( `while` ~ '(' ~ StatCtx.Expr ~ ')' ~> Trans1 )
    def Nice    : R[Ast.While] = rule( `while` ~ ch(':') ~ WSR0 ~ WithNiceScriptCall {() => StatCtx.SimpleExpr} ~> Trans2)

    rule (Standard | Nice)
  }

  def Let: R[Ast.Tiny] = rule {`let` ~ WSR0 ~ StatCtx.Expr ~ (WSR0 ~ ch(';')).? ~> SecondStr ~> Ast.Tiny}


  // Actors
  def ActorScriptCall: R[Ast.ScriptCall] = rule {ActorCall ~> Ast.ScriptCall}

  def ActorCall: R[Ast.ActorCall] = {
    def Trans1: Ast.ActorShortClause => Seq[Ast.ActorShortClause] = x => Seq(x)
    rule {wspStrR0("<<") ~ WLR0 ~ (ActorShortClause ~> Trans1 | ActorCaseClause.+) ~ wspStrR0(">>") ~> Ast.ActorCall}
  }

  def PatternWithGuard: R1 = rule {Pat ~ (ExprCtx.Guard.? ~> ExtractOpt) ~> Concat}

  def ActorCaseClause: R[Ast.ActorCaseClause] =
    rule( WLR0 ~ `case` ~ WithPattern {() => PatternWithGuard} ~> Concat ~ (`=>` ~ WithNormalInScript {() => Block} ~> Concat).? ~ (wspStrR0("==>") ~ ScriptBody).? ~> Ast.ActorCaseClause )
  
  def ActorShortClause: R[Ast.ActorShortClause] =
    rule( WSR0 ~ WithPattern {() => PatternWithGuard} ~ (`=>` ~ WithNormalInScript {() => Block} ~> Concat).? ~ (wspStrR0("==>") ~ ScriptBody).? ~> Ast.ActorShortClause )



  // Scala terms
  def ScalaTerm: R[Ast.ScriptCall] = rule {BlockExpr ~> Ast.Literal ~> Ast.ScriptCall | ScalaExprTerm | ScalaTupleTerm}

  def ScalaExprTerm : R[Ast.ScriptCall] = rule {wspChR0('(') ~ StatCtx.Expr ~ wspChR0(')') ~> Ast.Literal ~> Ast.ScriptCall}
  def ScalaTupleTerm: R[Ast.ScriptCall] = rule {'(' ~ OneOrMore(() => StatCtx.Expr, () => ',') ~ ')' ~> Concat3 ~> Ast.Literal ~> Ast.ScriptCall}
}