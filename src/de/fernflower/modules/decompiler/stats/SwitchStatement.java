/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.modules.decompiler.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.fernflower.code.SwitchInstruction;
import de.fernflower.code.cfg.BasicBlock;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.DecHelper;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.StatEdge;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.SwitchExprent;
import de.fernflower.util.InterpreterUtil;

public class SwitchStatement extends Statement {

	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	private List<Statement> caseStatements = new ArrayList<Statement>();
	
	private List<List<StatEdge>> caseEdges = new ArrayList<List<StatEdge>>();
	
	private List<List<ConstExprent>> caseValues = new ArrayList<List<ConstExprent>>();
	
	private StatEdge default_edge;
	
	private List<Exprent> headexprent = new ArrayList<Exprent>();
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	private SwitchStatement() {
		type = TYPE_SWITCH;
		
		headexprent.add(null);
	}
	
	private SwitchStatement(Statement head, Statement poststat) {
		
		this();  
		
		first = head;
		stats.addWithKey(head, head.id);

		// find post node
		Set<Statement> lstNodes = new HashSet<Statement>(head.getNeighbours(StatEdge.TYPE_REGULAR, DIRECTION_FORWARD));

		// cluster nodes
		if(poststat != null) {
			post = poststat;
			lstNodes.remove(post);
		}

		default_edge = head.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL).get(0);
		
		for(Statement st: lstNodes) {
			stats.addWithKey(st, st.id);
		}

	}
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public static Statement isHead(Statement head) {
		
		if(head.type == Statement.TYPE_BASICBLOCK && head.getLastBasicType() == Statement.LASTBASICTYPE_SWITCH) {
			
			List<Statement> lst = new ArrayList<Statement>(); 
			if(DecHelper.isChoiceStatement(head, lst)) {
				Statement post = lst.remove(0);
				
				for(Statement st : lst) {
					if(st.isMonitorEnter()) {
						return null;
					}
				}
				
				if(DecHelper.checkStatementExceptions(lst)) {
					return new SwitchStatement(head, post); 
				}
			}
		}
		
		return null;
	}
	
	public String toJava(int indent) {

		String indstr = InterpreterUtil.getIndentString(indent);
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuilder buf = new StringBuilder();
		buf.append(ExprProcessor.listToJava(varDefinitions, indent));
		buf.append(first.toJava(indent));
		
		if(isLabeled()) {
			buf.append(indstr+"label"+this.id+":" + new_line_separator);
		}
		
		buf.append(indstr+headexprent.get(0).toJava(indent)+" {" + new_line_separator);
		
		for(int i=0;i<caseStatements.size();i++) {
			
			Statement stat = caseStatements.get(i);
			List<StatEdge> edges = caseEdges.get(i);
			List<ConstExprent> values = caseValues.get(i);
			
			for(int j=0;j<edges.size();j++) {
				if(edges.get(j) == default_edge) {
					buf.append(indstr+"default:" + new_line_separator);
				} else {
					buf.append(indstr+"case "+ values.get(j).toJava(indent)+":" + new_line_separator);
				}
			}
			
			buf.append(ExprProcessor.jmpWrapper(stat, indent+1, false));
		}
		
		buf.append(indstr+"}" + new_line_separator);

		return buf.toString();
	}
	
	public void initExprents() {
		SwitchExprent swexpr = (SwitchExprent)first.getExprents().remove(first.getExprents().size()-1);
		swexpr.setCaseValues(caseValues);
		
		headexprent.set(0, swexpr);
	}
	
	public List<Object> getSequentialObjects() {

		List<Object> lst = new ArrayList<Object>(stats);
		lst.add(1, headexprent.get(0));
		
		return lst;
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(headexprent.get(0) == oldexpr) {
			headexprent.set(0, newexpr);
		}
	}
	
	public void replaceStatement(Statement oldstat, Statement newstat) {
		
		for(int i=0;i<caseStatements.size();i++) {
			if(caseStatements.get(i) == oldstat) {
				caseStatements.set(i, newstat);
			}
		}
		
		super.replaceStatement(oldstat, newstat);
	}
	
	public Statement getSimpleCopy() {
		return new SwitchStatement();
	}
	
	public void initSimpleCopy() {
		first = stats.get(0);
		default_edge = first.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL).get(0);
		
		sortEdgesAndNodes();
	}
	
	// *****************************************************************************
	// private methods
	// *****************************************************************************
	
	public void sortEdgesAndNodes() {
		
		HashMap<StatEdge, Integer> mapEdgeIndex = new HashMap<StatEdge, Integer>();
		
		List<StatEdge> lstFirstSuccs = first.getSuccessorEdges(STATEDGE_DIRECT_ALL);
		for(int i=0;i<lstFirstSuccs.size();i++) {
			mapEdgeIndex.put(lstFirstSuccs.get(i), i==0?lstFirstSuccs.size():i);
		}
		
		// case values
		BasicBlockStatement bbstat = (BasicBlockStatement)first;
		int[] values = ((SwitchInstruction)bbstat.getBlock().getLastInstruction()).getValues();
		
		List<Statement> nodes = new ArrayList<Statement>();
		List<List<Integer>> edges = new ArrayList<List<Integer>>();
		
		// collect regular edges
		for(int i=1;i<stats.size();i++) {
			
			Statement stat = stats.get(i);
			
			List<Integer> lst = new ArrayList<Integer>();
			for(StatEdge edge: stat.getPredecessorEdges(StatEdge.TYPE_REGULAR)) {
				if(edge.getSource() == first) {
					lst.add(mapEdgeIndex.get(edge));
				}
			}
			Collections.sort(lst);

			nodes.add(stat);
			edges.add(lst);
		}
		
		// collect exit edges
		List<StatEdge> lstExitEdges = first.getSuccessorEdges(StatEdge.TYPE_BREAK | StatEdge.TYPE_CONTINUE);
		while(!lstExitEdges.isEmpty()) {
			StatEdge edge = lstExitEdges.get(0); 
			
			List<Integer> lst = new ArrayList<Integer>();
			for(int i=lstExitEdges.size()-1;i>=0;i--) {
				StatEdge edgeTemp = lstExitEdges.get(i);
				if(edgeTemp.getDestination() == edge.getDestination() && edgeTemp.getType() == edge.getType()) {
					lst.add(mapEdgeIndex.get(edgeTemp));
					lstExitEdges.remove(i);
				}
			}
			Collections.sort(lst);

			nodes.add(null);
			edges.add(lst);
		}
		
		// sort edges (bubblesort)
	    for(int i=0;i<edges.size()-1;i++) {          
	        for(int j=edges.size()-1;j>i;j--) {     
	        	if(edges.get(j-1).get(0) > edges.get(j).get(0)) {
	        		edges.set(j, edges.set(j-1, edges.get(j)));
	        		nodes.set(j, nodes.set(j-1, nodes.get(j)));
	        	}
	        }
	    } 
		
		// sort statement cliques
	    for(int index = 0; index < nodes.size(); index++) {
			Statement stat = nodes.get(index);
			
			if(stat != null) {
				HashSet<Statement> setPreds = new HashSet<Statement>(stat.getNeighbours(StatEdge.TYPE_REGULAR, DIRECTION_BACKWARD));
				setPreds.remove(first);
				
				if(!setPreds.isEmpty()) {
					Statement pred = setPreds.iterator().next(); // at most one predecessor node besides the head
					for(int j=index+1;j<nodes.size();j++) {
						if(nodes.get(j) == pred) {
							nodes.add(j+1, stat);
							edges.add(j+1, edges.get(index));
							
							nodes.remove(index);
							edges.remove(index);
							index--;
							break;
						}
					}
				}
			}
		}
		
		// translate indices back into edges
	    List<List<StatEdge>> lstEdges = new ArrayList<List<StatEdge>>();
	    List<List<ConstExprent>> lstValues = new ArrayList<List<ConstExprent>>();
	    
	    for(List<Integer> lst: edges) {
	    	List<StatEdge> lste = new ArrayList<StatEdge>();
	    	List<ConstExprent> lstv = new ArrayList<ConstExprent>();
	    	
	    	List<StatEdge> lstSuccs = first.getSuccessorEdges(STATEDGE_DIRECT_ALL);
	    	for(Integer in: lst) {
	    		int index = in==lstSuccs.size()?0:in;
	    		
	    		lste.add(lstSuccs.get(index));
	    		lstv.add(index==0?null:new ConstExprent(values[index-1], false));
	    	}
	    	lstEdges.add(lste);
	    	lstValues.add(lstv);
	    }
	    
	    // replace null statements with dummy basic blocks
	    for(int i=0;i<nodes.size();i++) {
	    	if(nodes.get(i) == null) {
				BasicBlockStatement bstat = new BasicBlockStatement(new BasicBlock(
						DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
	    		
				StatEdge sample_edge = lstEdges.get(i).get(0);
				
				bstat.addSuccessor(new StatEdge(sample_edge.getType(), bstat, sample_edge.getDestination(), sample_edge.closure));
				
				for(StatEdge edge : lstEdges.get(i)) {
					
					edge.getSource().changeEdgeType(DIRECTION_FORWARD, edge, StatEdge.TYPE_REGULAR);
					edge.closure.getLabelEdges().remove(edge);
					
					edge.getDestination().removePredecessor(edge);
					edge.getSource().changeEdgeNode(DIRECTION_FORWARD, edge, bstat);
					bstat.addPredecessor(edge);
				}
				
				nodes.set(i, bstat);
				stats.addWithKey(bstat, bstat.id);
				bstat.setParent(this);
	    	}
	    }
	    
	    caseStatements = nodes;
	    caseEdges = lstEdges;
	    caseValues = lstValues;
	}

	public List<Exprent> getHeadexprentList() {
		return headexprent;
	}
	
	public Exprent getHeadexprent() {
		return headexprent.get(0);
	}

	public List<List<StatEdge>> getCaseEdges() {
		return caseEdges;
	}

	public List<Statement> getCaseStatements() {
		return caseStatements;
	}

	public StatEdge getDefault_edge() {
		return default_edge;
	}

	public List<List<ConstExprent>> getCaseValues() {
		return caseValues;
	}

}