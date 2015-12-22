package subscript.swing

import java.awt.{Font, BasicStroke, Stroke, Color => AWTColor}
import java.awt.geom.AffineTransform
import java.util.concurrent.Executors

import javax.swing.JList

import scala.collection.mutable.ListBuffer
import scala.swing._

// import subscript.swing.Scripts._
import subscript.DSL._
import subscript.vm._
import subscript.vm.executor._
import subscript.vm.model.template._
import subscript.vm.model.template.concrete._
import subscript.vm.model.callgraph._

/*
 * Graphical script debugger
 * 
 * Operation mode 1: pass the class to be debugged as parameter from the command line
 * 
 *   execute the main method of GraphicalDebugger 
 *   with optional first argument: -s "text to be displayed in description text field"
 *   and then argument: the package+class name of the object to be debugged
 *   and later arguments: the arguments to be passed to the debugged object
 * 
 * Operation mode 2: pass the debugger as an argument to the subscript.vm._execute method:
 * 
 * 	  val debugger = new GraphicalDebugger
 *    _execute(scriptDef, debugger, executor)
 */

trait GraphicalDebugger extends MsgListener {

  def traceLevel = 2
  def otherTraceLevel = 1
  def trace(s: => Any) = if (traceLevel>0) {println(s); Console.flush}
  
  private var _otherScriptExecutor: ScriptExecutor[_] = null
  var vmThread: Thread = null
  
  def otherScriptExecutor = _otherScriptExecutor
  override def attach(p: MsgPublisher) {
    super.attach(p)
    _otherScriptExecutor = p.asInstanceOf[ScriptExecutor[_]]
    _otherScriptExecutor.traceLevel = otherTraceLevel
    _otherScriptExecutor.name = "otherScriptExecutor"
  }
  
  def main(args: Array[String]): Unit = {
    var lArgs = args
    if (lArgs.isEmpty) return
    ScriptExecutorFactory.addScriptDebugger(this)
    top.visible = true
    
    lArgs.head match {
      case "-s" => lArgs = lArgs.tail; if (lArgs.isEmpty) return; descriptionTF.text = lArgs.head
                   lArgs = lArgs.tail; if (lArgs.isEmpty) return
      case _ =>
    }
    vmThread = new Thread{override def run={
      live
      quit
    }}
    vmThread.start()
    
    val className = lArgs.head
    try {
      val c = Class.forName(className) // TBD: should be a swing application
      val m = c.getMethod("main", classOf[Array[String]])
      println("debugger: "+this.getClass.getCanonicalName+" on: "+className)
      m.invoke(null, lArgs.tail)
    }
    catch {
      case e: ClassNotFoundException => println("Could not find class "+className)
      case other: Throwable => other.printStackTrace
    }
  }

  def live: Unit
  def quit: Unit

  var messageBeingHandled = false
  var currentMessage: CallGraphMessage = null
  
  def interestingContinuationInternals(c: Continuation): List[String] = {
     var ss: List[String] = Nil
     if (c != null) {
          if ( c.success       != null) ss ::= "Success"
          if (!c.aaHappeneds  .isEmpty) ss ::= "AA Happened"
          if (!c.deactivations.isEmpty) ss ::= "Deactivations"
          if ( c.activation    != null) ss ::= "Activation"
     }
     ss
  }
  
  val exitButton = new Button("Exit"  ) {enabled = false; defaultCapable = false; focusable=false }
  val stepButton = new Button("Step"  ) {enabled = false}
  
  //"Activation  "
  //"Deactivation"
  //"Continuation"
  //"AAHappened     "
  //"Success     "
  //"Break       "
  //"Exclude     "
  
  
  val checkBox_step_Activation     = new CheckBox {text = "Act" ; selected = true}
  val checkBox_step_Deactivation   = new CheckBox {text = "Dea" ; selected = true}
  val checkBox_step_CFToBeExecuted = new CheckBox {text = "AAT" ; selected = true}
  val checkBox_step_Continuation   = new CheckBox {text = "Cnt" ; selected = true}
  val checkBox_step_AAHappened     = new CheckBox {text = "AAH" ; selected = true}
  val checkBox_step_Success        = new CheckBox {text = "Scs" ; selected = true}
  val checkBox_step_Break          = new CheckBox {text = "Brk" ; selected = true}
  val checkBox_step_Exclude        = new CheckBox {text = "Exc" ; selected = true}
  val checkBox_step_Wait           = new CheckBox {text = "Idle"; selected = true}

  val checkBox_log_Activation      = new CheckBox {text = "Act" ; selected = true}
  val checkBox_log_Deactivation    = new CheckBox {text = "Dea" ; selected = true}
  val checkBox_log_CFToBeExecuted  = new CheckBox {text = "AAT" ; selected = true}
  val checkBox_log_Continuation    = new CheckBox {text = "Cnt" ; selected = true}
  val checkBox_log_AAHappened      = new CheckBox {text = "AAH" ; selected = true}
  val checkBox_log_Success         = new CheckBox {text = "Scs" ; selected = true}
  val checkBox_log_Break           = new CheckBox {text = "Brk" ; selected = true}
  val checkBox_log_Exclude         = new CheckBox {text = "Exc" ; selected = true}
  val checkBox_log_Wait            = new CheckBox {text = "Idle"; selected = true}

  val buttonsPanel = new BoxPanel(Orientation.Vertical) {
    contents += new Label("Step:")
    contents += checkBox_step_Activation  
    contents += checkBox_step_Deactivation
    contents += checkBox_step_CFToBeExecuted  
    contents += checkBox_step_Continuation
    contents += checkBox_step_AAHappened   
    contents += checkBox_step_Success     
    contents += checkBox_step_Break       
    contents += checkBox_step_Exclude     
    contents += checkBox_step_Wait
    contents += new Label("  ---  ")
    contents += new Label("Log:")
    contents += checkBox_log_Activation  
    contents += checkBox_log_Deactivation
    contents += checkBox_log_CFToBeExecuted  
    contents += checkBox_log_Continuation
    contents += checkBox_log_AAHappened     
    contents += checkBox_log_Success     
    contents += checkBox_log_Break       
    contents += checkBox_log_Exclude     
    contents += checkBox_log_Wait
  }
  val callGraphPanel = new Panel {
    background = AWTColor.white
    preferredSize  = new Dimension(3000,2000)
    override def paint(g: Graphics2D) {
        g.setColor(AWTColor.white)
        g.fillRect(0, 0, size.width, size.height)
        try onPaintCallGraph(g)
        catch {case ex:Exception => ex.printStackTrace()}
    }
  }
  val templateTreesPanel = new Panel {
    background = AWTColor.white
    preferredSize  = new Dimension(4600,900)
    override def paint(g: Graphics2D) {
        g.setColor(AWTColor.white)
        g.fillRect(0, 0, size.width, size.height)
        onPaintTemplateTrees(g)
    }
  }
  val fixedWidthFont = new Font("Monaco", Font.PLAIN, 14)
  val currentMsgFont = new Font("Monaco", Font.BOLD , 14)
 
  val     normalFont = new Font("Arial" , Font.BOLD, 16)
  val      smallFont = new Font("Arial" , Font.BOLD, 13)
  val   normalStroke = new BasicStroke(1)
  val      fatStroke = new BasicStroke(3)
  
  val lightOrange = new AWTColor(255, 238, 220)
  val lightGreen  = new AWTColor(220, 255, 220)
  val lightBlue   = new AWTColor(220, 220, 255)
  val lightRed    = new AWTColor(255, 220, 220)
  val lightPurple = new AWTColor(255, 220, 255)
  
  def fillColor(n: CallGraphNode, defaultColor: AWTColor, allowOverride: Boolean) = 
         if (allowOverride && n.isExecuting ) lightPurple  
    else if (allowOverride && n.isActionBusy) lightRed 
    else                                      defaultColor

  
  def drawStringCentered(g: Graphics2D, s: String, cx: Int, cy: Int) {
    val sw = g.getFontMetrics.stringWidth(s)
    val sh = g.getFontMetrics.getHeight
    g.drawString(s, cx-sw/2, cy+sh/2)
  }
  def drawStringTopLeft(g: Graphics2D, s: String, x: Int, y: Int) {
    val sh = g.getFontMetrics.getHeight
    g.drawString(s, x, y+sh/2)
  }
  def drawStringTopRight(g: Graphics2D, s: String, x: Int, y: Int) {
    val sw = g.getFontMetrics.stringWidth(s)
    val sh = g.getFontMetrics.getHeight
    g.drawString(s, x-sw, y+sh/2)
  }
  def emphasize_g(g: Graphics2D, doIt: Boolean) {
    if (doIt) {
      g.setColor(AWTColor.red)
      g.setStroke(fatStroke)
    }
    else {
      g.setColor(AWTColor.black)
      g.setStroke(normalStroke)
    }
  }
  def onPaintTemplateTrees(g: Graphics2D) {
    val GRID_W  =  50
	val GRID_H  =  33
	val RATIO_W = 0.8
	val RATIO_H = 0.67
	val BOX_W   = (GRID_W * RATIO_W).toInt
	val BOX_H   = (GRID_H * RATIO_H).toInt
    val hOffset = (GRID_W - BOX_W)/2
    val vOffset = (GRID_H - BOX_H)/2
  
    def emphasize(doIt: Boolean) {emphasize_g(g, doIt)}

    def getScriptTemplates: List[T_script] = {
      val lb = new ListBuffer[T_script]
      def getScriptTemplates(n: CallGraphNode): Unit = {
        n match {case ns: ScriptNode[_] => if (!lb.exists(_.name.name==ns.template.name.name)) lb += ns.template case _ =>}
        n match {case pn: CallGraphNode => pn.forEachChild{getScriptTemplates(_)} case _ =>}
      } 
      getScriptTemplates(rootNode)
      lb.toList
    }
    def drawEdge(p: TemplateNode, pwx: Double, pwy: Int, 
                 c: TemplateNode, cwx: Double, cwy: Int): Unit = {
        
        val pHCenter = (pwx*GRID_W).toInt + BOX_W/2 + hOffset
        val pBottom  =  pwy*GRID_H        + BOX_H   + vOffset
        val cHCenter = (cwx*GRID_W).toInt + BOX_W/2 + hOffset
        val cTop     =  cwy*GRID_H                  + vOffset
        
        val x1       = pHCenter
        val y1       = pBottom
        val x2       = cHCenter
        val y2       = cTop
        
        g.drawLine(x1, y1, x2, y2)
      }
    def drawTemplateTree(t: TemplateNode, xGrid: Double, yGrid: Int): (Double, Double) = {
        var resultW = 0d
        var childHCs = new ListBuffer[Double]
	    if (t.children.isEmpty) resultW = 1d
	    else {
             t.children.foreach{ ct =>
	            val (childW, childHC) = drawTemplateTree(ct, xGrid+resultW, yGrid+1)
	            resultW  += childW
	            childHCs += childHC
              }
	    }
        val thisX   = xGrid+(resultW-1)/2 
        
        val s        = t.toString()
        val sw       = g.getFontMetrics.stringWidth(s)
        val boxWidth = math.max(sw + 4, BOX_W) // ensure the box fits the string
        val hOffset1 = (GRID_W - boxWidth)/2
        val boxLeft  = (thisX*GRID_W).toInt+hOffset1
        val boxTop   = yGrid*GRID_H+vOffset
        val hCenter  = boxLeft + boxWidth/2
        val vCenter  = boxTop  + BOX_H/2

        val r = new Rectangle(boxLeft, boxTop, boxWidth, BOX_H)
        //val n = if  (currentMessage==null||currentMessage.node==null) null 
        //        else currentMessage.node.asInstanceOf[CallGraphNode]    strange NPE's
        val n1 = if  (currentMessage==null) null 
                 else currentMessage.node
        val n = n1.asInstanceOf[CallGraphNode]
        val nameFont = t match {
          case _:T_call[_] | _:T_script => smallFont
          case _        => normalFont
        }

        // this check goes a bit wrong, resulting in too many template nodes being highlighted occasionally.
        // The problem is that almost "equal" templates are still different per node, and we don't want to draw them all.
        // Sometime a solution will be found
        val isCurrentTemplate = currentMessage            != null    && 
                                n                         != null    && 
                                n.template                != null    &&
                                (n.template eq t)
//                                ( n.template.root == null   && t.root == null
//                                ||n.template.root.name      == t.root.name   ) && 
//                                n.template.owner          != null             &&
//                                n.template.owner.getClass == t.owner.getClass && 
//                                n.template.indexInScript  == t.indexInScript
                                
        g.setColor(fillColor(n, lightOrange, isCurrentTemplate)) 
        g fill r
        emphasize(isCurrentTemplate)
        g draw r
        emphasize (false)
        g.setFont(nameFont)
        drawStringCentered(g, s, hCenter, vCenter-3)
        (t.children zip childHCs).foreach{ c_hc: (TemplateNode, Double) =>
          drawEdge(t, thisX, yGrid, c_hc._1, c_hc._2, yGrid+1)
        }
        (resultW, thisX)
    }
    
    if (otherScriptExecutor==null) return
    g.setFont(normalFont)
    var currentXGrid = 0d // drawn width of template trees so far, in Grid coordinates
    getScriptTemplates.foreach { t => currentXGrid += drawTemplateTree(t, currentXGrid, 0)._1}
  }
  def onPaintCallGraph(g: Graphics2D) {
    val GRID_W  =  90
	val GRID_H  =  43
	val RATIO_W = 0.75
	val RATIO_H = 0.6
	val BOX_W   = (GRID_W * RATIO_W).toInt
	val BOX_H   = (GRID_H * RATIO_H).toInt
    val hOffset = (GRID_W - BOX_W)/2
    val vOffset = (GRID_H - BOX_H)/2

      def emphasize(doIt: Boolean) {emphasize_g(g, doIt)}
      
      g.setFont(normalFont)

      def drawArrow(x1: Int, y1: Int, x2: Int, y2: Int, s: String) {
        val dx    = x2 - x1
        val dy    = y2 - y1
        val angle = math.atan2(dy, dx)
        val len   = math.sqrt(dx*dx + dy*dy).intValue
        val ARROW_HEAD_W = 5
        val ARROW_HEAD_L = 15
        
        emphasize(s != null)
        if (s != null) {
          g.setFont(normalFont)
          drawStringTopLeft(g, s, x1 + dx/2 + 9, y1 + dy/2 - 2)
        }
        val oldTransform = g.getTransform()
        val at = oldTransform.clone.asInstanceOf[AffineTransform]
        at.concatenate(AffineTransform.getTranslateInstance(x1, y1))
        at.concatenate(AffineTransform.getRotateInstance(angle))
        g.setTransform(at)

        // Draw horizontal arrow starting in (0, 0)
        g.drawLine(0, 0, len, 0)
        
        if (s != null) {
          g.fillPolygon(Array(len, len-ARROW_HEAD_L, len-ARROW_HEAD_L, len),
                        Array(  0,    -ARROW_HEAD_W,     ARROW_HEAD_W,   0), 4)
        }
        g.setTransform(oldTransform)
        emphasize(false)
      }
      def getBreakText(a: ActivationMode.ActivationModeType): String = {
        a match {
          case ActivationMode.Optional => "Optional Break"
          case _ => "Break"
        }
      }
      def drawEdge(p: CallGraphNode, pwx: Double, pwy: Int, 
                   c: CallGraphNode, cwx: Double, cwy: Int): Unit = {
        
        val pHCenter = (pwx*GRID_W).toInt + BOX_W/2 + hOffset
        val pBottom  =  pwy*GRID_H        + BOX_H   + vOffset
        val cHCenter = (cwx*GRID_W).toInt + BOX_W/2 + hOffset
        val cTop     =  cwy*GRID_H                  + vOffset
        
        val x1       = pHCenter
        val y1       = pBottom
        val x2       = cHCenter
        val y2       = cTop
        
        val text:String = if (currentMessage==null) null else
          currentMessage match { // node.index is not checked by node.equals!!!!
            case AAHappened(mp,mc,mode) if(p.index==mp.index&&c.index==mc.index)  => "AA Happened"
            case SuccessMsg(mp,null)                                              =>  null
            case SuccessMsg(mp,mc)      if (p.index==mp.index&&c.index==mc.index) => "Success"
            case Break     (mp,mc,mode) if (p.index==mp.index&&c.index==mc.index) => getBreakText(mode)
            case Exclude   (mp,mc)      if (p.index==mp.index&&c.index==mc.index) => "Exclude"
            case _                                                                => null
          }
        
        val doDownwards = currentMessage.isInstanceOf[Exclude]
        if (doDownwards) drawArrow(x1, y1, x2, y2, text)
        else             drawArrow(x2, y2, x1, y1, text)
        
        g.setStroke(normalStroke)
        g.setColor (AWTColor.black)
      }
      def drawContinuationTexts(n: CallGraphNode, boxRight: Int, boxTop: Int) = {
        val x = boxRight + 3
        var y = boxTop
        n match {
          case nn: N_n_ary_op if (nn.continuation != null) =>     
            val fontMetrics = g.getFontMetrics
            interestingContinuationInternals(nn.continuation).foreach{s: String=>drawStringTopLeft(g, s, x, y); y += fontMetrics.getHeight - 2}
          case _ => if (currentMessage!=null&&currentMessage.node==n) currentMessage match {
            case s: SuccessMsg if (s.child==null) => drawStringTopLeft(g, "Success"    , x, y) 
            case a: AAHappened if (a.child==null) => drawStringTopLeft(g, "AA Happened", x, y) 
            case _ => 
          }
        }
      }
	  def drawTree[T <: TemplateNode](n: CallGraphNode, xGrid: Double, yGrid: Int): (Double, Double) = {
        var resultW = 0d // drawn width of this subtree
        var childHCs = new ListBuffer[Double]
        
        val isCurrentNode = currentMessage != null && currentMessage.node.asInstanceOf[CallGraphNode].index == n.index
	    n match {
	      case p:CallGraphNode => 
	        val pcl=p.children.length
	        if (pcl==0) {
	          resultW = 1
	        }
	        else {
              p.children.foreach{ c =>
	            val (childW, childHC) = drawTree(c, xGrid+resultW, yGrid+1)
	            resultW  += childW
	            childHCs += childHC
              }
	        }
	      case _ => resultW = 1
	    }
        val thisX     = xGrid+(resultW-1)/2 
        val boxLeft   = (thisX*GRID_W).toInt+hOffset
        val boxTop    = yGrid*GRID_H+vOffset
        val boxRight  = boxLeft + BOX_W
        val boxBottom = boxTop  + BOX_H
        val hCenter   = boxLeft + BOX_W/2
        val vCenter   = boxTop  + BOX_H/2
        
        val s: String = n match {
          case ns: ScriptNode[_]  => ns.template.name.name
          case no: N_n_ary_op => no.template.kind + (if (no.isIteration) " ..." else "")
          case _              => n .template.kind
        }
        val nameFont = n match {
          case _: Script[_] | _: N_call[_] => smallFont
          case _                           => normalFont
        }
        
        val r = new Rectangle(boxLeft, boxTop, BOX_W, BOX_H)
        g.setColor(fillColor(n, lightGreen, true)) 
        g fill r
        emphasize(isCurrentNode)
        g draw r 
        if (isCurrentNode) {
          currentMessage match {
            case dm: Deactivation =>
              n match {
                case nn: N_n_ary_op if(dm.child!=null) => // will get Continuation
                case pn: CallGraphNode if (pn.children.length>0) =>
                case _ => // strike through with an "X"
                  g.drawLine(boxLeft, boxTop   , boxRight, boxBottom)
                  g.drawLine(boxLeft, boxBottom, boxRight, boxTop   )
              }
            case _ =>
          }
        }
        emphasize(false)
        g.setFont(smallFont)
        drawContinuationTexts(n, boxRight, boxTop)
        drawStringTopLeft (g, n.index.toString, boxLeft+2, boxTop+5)
        if (n.hasSuccess) drawStringTopRight(g, "S", boxRight-1, boxTop+5)
        g.setFont(nameFont)
        drawStringCentered(g, s, hCenter, vCenter)
	    n match {
          case nn:  N_n_ary_op => 
            if (nn.activationMode!=ActivationMode.Active) {
              val s = if (nn.activationMode!=ActivationMode.Inactive) "-" else "."
              g.setFont(smallFont)
              drawStringTopLeft (g, s, boxRight-7, boxTop+5)
            }
	      case _ =>
        }
	    n match {
	      case p:CallGraphNode if !p.children.isEmpty => 
	        (p.children zip childHCs).foreach{ c_hc: (CallGraphNode, Double) =>
	          drawEdge(n, thisX, yGrid, c_hc._1, c_hc._2, yGrid+1)
	        }
	      case _ =>
	    }
        (resultW, thisX)
	  }
      if (otherScriptExecutor!=null) drawTree(rootNode, 0, 0)
  }
  
  val maxLogListMsgs    = 5000
  val logListMsgsCleanups = 1000
  val msgLogListModel   = new javax.swing.DefaultListModel[String]
  val msgQueueListModel = new javax.swing.DefaultListModel[CallGraphMessage]
  val currentMessageTF  = new TextField {
    //preferredSize       = new Dimension(400,24)
    //minimumSize         = preferredSize
    editable            = false
    font                = currentMsgFont
    background          = lightBlue
    horizontalAlignment = scala.swing.Alignment.Left
  }
  val msgLogList        = new ListBuffer[String]
  val msgQueueList      = new ListBuffer[String]
  val msgLogListView    = new ListView(msgLogList) {
    font                = fixedWidthFont
    peer.asInstanceOf[JList[String]].setModel(msgLogListModel)   // Compiler throws an exception without a cast
  }
  val msgQueueListView  = new ListView(msgQueueList) {
    font                = fixedWidthFont
    peer.asInstanceOf[JList[CallGraphMessage]].setModel(msgQueueListModel)
  }
  val msgLogListViewScrollPane   = new ScrollPane
  {
  	contents                     = msgLogListView
  	verticalScrollBarPolicy      = ScrollPane.BarPolicy.Always
  }
  val msgQueueListViewScrollPane = new ScrollPane
  {
  	contents                     = msgQueueListView
  	verticalScrollBarPolicy      = ScrollPane.BarPolicy.Always
  }
  val borderPanelMsgs = new BorderPanel {
    add(msgLogListViewScrollPane, BorderPanel.Position.Center)
    add(currentMessageTF        , BorderPanel.Position.South)
  }
  
  var splitPaneMain: SplitPane = null
  val doTemplateTreeTopLeft = true
  
  if (doTemplateTreeTopLeft) {  
    val splitPaneMsgs      = new SplitPane(scala.swing.Orientation.Horizontal, borderPanelMsgs,       msgQueueListViewScrollPane) {dividerLocation  = 250}
    val splitPaneTreesMsgs = new SplitPane(scala.swing.Orientation.Horizontal, new ScrollPane(templateTreesPanel), splitPaneMsgs) {dividerLocation  = 250}
    val splitPaneLeft      = new SplitPane(scala.swing.Orientation.Vertical  , buttonsPanel                 , splitPaneTreesMsgs) {dividerLocation  =  60}
    splitPaneMain          = new SplitPane(scala.swing.Orientation.Vertical  ,   splitPaneLeft,   new ScrollPane(callGraphPanel)) {dividerLocation  = 400}
  }
  else {  
    val splitPaneGraphs = new SplitPane(scala.swing.Orientation.Horizontal, new ScrollPane(templateTreesPanel), 
                                                                            new ScrollPane(callGraphPanel)              ) {dividerLocation  = 178}
    val splitPaneMsgs   = new SplitPane(scala.swing.Orientation.Horizontal, borderPanelMsgs,  msgQueueListViewScrollPane) {dividerLocation  = 350}
    splitPaneMain       = new SplitPane(scala.swing.Orientation.Vertical,     splitPaneMsgs,             splitPaneGraphs) {dividerLocation  = 240}
  } 
  val descriptionTF     = new TextField {
    preferredSize       = new Dimension(400,24)
    editable            = false
    font                = fixedWidthFont //normalFont
  }
  val autoCheckBox      = new CheckBox {
    text                = "Auto"
  //selected            = true
  }
  val speedSlider       = new Slider {
    min                 =   0
    max                 =  10
    value               =   5
  }
  
  val top               = new Frame {
    title               = "Subscript Graphical Debugger"
    location            = new Point    (0,0)
    preferredSize       = new Dimension(900,700)
    contents            = new BorderPanel {
      add(new FlowPanel(descriptionTF, stepButton, autoCheckBox, speedSlider, exitButton), BorderPanel.Position.North) 
      add(splitPaneMain, BorderPanel.Position.Center)
    }
  }
  
  def sleep(duration_ms: Long) = try {Thread.sleep(duration_ms)} catch {case e: InterruptedException => println("sleep interrupted")}
  def confirmExit: Boolean = Dialog.showConfirmation(exitButton, "Are you sure?", "About to exit")==Dialog.Result.Yes
  
  def shouldStep: Boolean =
    currentMessage match {
      case Activation(_)       => checkBox_step_Activation    .selected
      case Deactivation(_,_,_) => checkBox_step_Deactivation  .selected
      case CFToBeExecuted(_)   => checkBox_step_CFToBeExecuted.selected
      case AAHappened(_,_,_)   => checkBox_step_AAHappened    .selected
      case SuccessMsg(_,_)     => checkBox_step_Success       .selected
      case Break(_,_,_)        => checkBox_step_Break         .selected
      case Exclude(_,_)        => checkBox_step_Exclude       .selected
      case c:Continuation      => checkBox_step_Continuation  .selected && !interestingContinuationInternals(c).isEmpty
      case _                   => false    
    }
  
  def MAX_STEP_DELAY_SEC = 5

  def stepSleep_ms = (math.pow((speedSlider.max-speedSlider.value) / speedSlider.max.toDouble, 3) * MAX_STEP_DELAY_SEC * 1000).intValue
    
  def waitForStepTimeout = {
    try {
      val sleepPart_ms = 10
      var slept_ms     = 0
      while(slept_ms<stepSleep_ms) {
          Thread.sleep(sleepPart_ms);
          slept_ms += sleepPart_ms
      }
    }
    catch {case e: InterruptedException => }
//    trace(f"waitForStepTimeout done")
  }
  def logMessage_GUIThread(m: String, msg: CallGraphMessage) {
    
      if (msg match {
        case Activation(_)       => checkBox_log_Activation    .selected
        case Deactivation(_,_,_) => checkBox_log_Deactivation  .selected
        case CFToBeExecuted(_)   => checkBox_log_CFToBeExecuted.selected
        case AAHappened(_,_,_)   => checkBox_log_AAHappened    .selected
        case SuccessMsg(_,_)     => checkBox_log_Success       .selected
        case Break(_,_,_)        => checkBox_log_Break         .selected
        case Exclude(_,_)        => checkBox_log_Exclude       .selected
        case c:Continuation      => checkBox_log_Continuation  .selected
        case _                   => false
      }) {    
        var runnable = new Runnable {
          def run(): Unit = {logMessage(m, msg)}
        }
        javax.swing.SwingUtilities.invokeLater(runnable)
      }
  }
  def logMessage(m: String, msg: CallGraphMessage) {
    if (msgLogListModel.size > maxLogListMsgs)
      msgLogListModel.removeRange(0, logListMsgsCleanups)
    msgLogListModel.addElement(m + " " + msg)
    msgLogListViewScrollPane.verticalScrollBar.value = msgLogListViewScrollPane.verticalScrollBar.maximum
    msgQueueListModel.clear
    
    // Sorting the list of scriptExecutor messages
    // before rendering
    val ord = otherScriptExecutor.msgQueue.ordering
    val orderedMessages = callGraphMessages.toList.sorted(ord).reverse
    orderedMessages.foreach(msgQueueListModel.addElement(_)) 
  }
  def updateDisplay = {
    var s = if (currentMessage==null) "." else currentMessage.toString
    if (s.length>50) s = s.substring(0, 50) + "..."
    currentMessageTF.text = s
    logMessage (">>", currentMessage)
    callGraphPanel    .repaint()
    templateTreesPanel.repaint()
  }
  def doesThisAllowToBeDebugged = false // overridden in GraphicalDebugger2
  val myScriptExecutor = ScriptExecutorFactory.createScriptExecutor[Any](doesThisAllowToBeDebugged)
  myScriptExecutor.name = "myScriptExecutor";


  var exitConfirmed = false 

  def kickExecutors = {kickExecutor(myScriptExecutor); kickExecutor(otherScriptExecutor)}
  def kickExecutor(executor: ScriptExecutor[_]) = {
    if (executor!=null) {                       //trace(f"kickExecutor start: $executor")
      executor.synchronized{executor.notify()}; //trace(f"kickExecutor   end: $executor")
    }  
  }
  
  object MessageStatusLock
  def messageBeingHandled(value: Boolean): Unit = {
//    trace(f" messageBeingHandled($value%5s) start")
    MessageStatusLock.synchronized {
      //trace(f" messageBeingHandled($value%5s) synchronized")
      // maybe needed because sometimes (=race condition) the other script executor would stay waiting
      kickExecutor(otherScriptExecutor)
      messageBeingHandled = value
      MessageStatusLock.notifyAll()
    }
//    trace(f" messageBeingHandled($value%5s) end")
  }
  
  def awaitMessageBeingHandled(value: Boolean) = {
    //trace(f"awaitMsgBeingHandled($value%5s) start")
    MessageStatusLock.synchronized {
      //trace(f"awaitMsgBeingHandled($value%5s) wait start")
      while (messageBeingHandled != value) MessageStatusLock.wait()
    //trace(f"awaitMsgBeingHandled($value%5s) wait end")
    }
    //trace(f"awaitMsgBeingHandled($value%5s) end")
  }
    
  import scala.collection.JavaConversions._
  def dumpStacks = for ((thread,sts) <- mapAsScalaMap(Thread.getAllStackTraces)) {
     trace(thread)
     sts foreach {trace(_)}
     trace("")
   }
   def dumpExecutors = {dumpExecutor(myScriptExecutor); dumpExecutor(otherScriptExecutor);}
   def dumpExecutor(executor: ScriptExecutor[_]) = {
     if (traceLevel > 1) {
       trace(executor)
       trace("====================")
       val debugger = new SimpleScriptDebuggerClass
       debugger.attach(executor)
       debugger.traceLevel = 4
       debugger.traceTree
       debugger.traceMessages
       trace("")
     }
   }
   
/*  
  override def _live  = _script(this, 'live) {_par_or2(_seq(_threaded0{awaitMessageBeingHandled(true)}, 
                                                      _if0{shouldStep} (_par_or(_seq(_at{gui} (_tiny0{updateDisplay}), _stepCommand), 
                                                                               _if_else0{autoCheckBox.selected}(_threaded0{waitForStepTimeout}, _deadlock))), 
                                                      _normal0{messageBeingHandled=false}, 
                                                      _loop
                                                     ), 
                                                  _exitDebugger
                                                )}
  def   _stepCommand  = _script(this, 'stepCommand ) {_clicked(stepButton)}
  def   _exitCommand  = _script(this, 'exitCommand ) {_clicked(exitButton)} // windowClosing
  def   _exitDebugger = _script(this, 'exitDebugger) {_seq(  _exitCommand, _at{gui}(_while0{!confirmExit}))}
//def   _exitDebugger = _script('exitDebugger) {_seq(  _exitCommand, _at{gui}(_normal{exitConfirmed=confirmExit}), _while{!exitConfirmed})}
  
  */
  def callGraphMessages = otherScriptExecutor.msgQueue.collection
  def rootNode          = otherScriptExecutor.rootNode
  
  override def messageHandled(m: CallGraphMessage): Unit = {
    currentMessage = m
    messageBeingHandled(true) 
    awaitMessageBeingHandled(false)
    currentMessage = null
  }
  override def messageQueued      (m: CallGraphMessage                 ) = logMessage_GUIThread("++", m)
  override def messageDequeued    (m: CallGraphMessage                 ) = logMessage_GUIThread("--", m)
  override def messageContinuation(m: CallGraphMessage, c: Continuation) = logMessage_GUIThread("**", c)
  override def messageAwaiting: Unit = {
    trace(f"messageAwaiting")
    if (checkBox_log_Wait .selected) currentMessageTF.text = "Waiting..."
    if (checkBox_step_Wait.selected) callGraphPanel.repaint()
}
}
