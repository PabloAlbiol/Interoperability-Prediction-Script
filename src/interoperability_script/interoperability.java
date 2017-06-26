/*
 * Copyright (c) 2014 CEA and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Christian W. Damus (CEA) - initial API and implementation
 *   
 * Modifications:
 *   Pablo Albiol Graullera
 *
 */

package interoperability_script;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil;

// GraphStream (a dynamic graph library). Used to create the interoperability graphs.
// http://graphstream-project.org/
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.graphstream.ui.view.Viewer;


/**
 * A Java program that may be run stand-alone (with the required EMF and UML2
 * bundle JARs on the classpath).
 */

public class interoperability 
{

	public static boolean DEBUG = true;
	private static MultiGraph graph;
	private static File outputDir;
	private static final ResourceSet RESOURCE_SET;
	private static PrintWriter writer;
	private static PrintWriter writerCN;
	private static int numElements = 0;
	private static int numClasses = 0;
	private static int numCN = 0;
	private static int numCNSatisfied = 0;
	private static boolean flag_language = true;
	private static boolean flag_address = true;
	private static boolean drawGraph = true;
	private static int numSubCNTotal = 0;
	private static EList<org.eclipse.uml2.uml.Class> actors_stats = new BasicEList<org.eclipse.uml2.uml.Class>();
	private static EList<org.eclipse.uml2.uml.Class> mps_stats = new BasicEList<org.eclipse.uml2.uml.Class>();
	private static EList<org.eclipse.uml2.uml.Class> lang_stats = new BasicEList<org.eclipse.uml2.uml.Class>();
	
	static 
	{
		// Create a resource-set to contain the resource(s) that we load and
		// save
		RESOURCE_SET = new ResourceSetImpl();
		
		// Initialize registrations of resource factories, library models,
		// profiles, Ecore metadata, and other dependencies required for
		// serializing and working with UML resources. This is only necessary in
		// applications that are not hosted in the Eclipse platform run-time, in
		// which case these registrations are discovered automatically from
		// Eclipse extension points.
		UMLResourcesUtil.init(RESOURCE_SET);
	}

	/**
	 * The main program. It expects one argument, which is the local filesystem
	 * path of a directory in which to load and save files.
	 * 
	 * @param the program arguments, which must consist of a single filesystem
	 * path.
	 */
	public static void main(String[] args)
	
	throws Exception 
	{
		if (!processArgs(args)) 
		{
			System.exit(1);
		}
		
		// Render engine (high quality)
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		
		// Create MultiGraph
		graph = new MultiGraph("Interoperability");
        graph.addAttribute("ui.quality");
        graph.addAttribute("ui.antialias");
        graph.addAttribute("ui.stylesheet", "url('file:///C:/Users/User/workspace/interoperability_script/styleGraph.css')"); // Modify path
        
		// Create log text file
		writer = new PrintWriter("results.txt", "UTF-8");
		// Create log text file CN
		writerCN = new PrintWriter("cntable.txt", "UTF-8");
		writerCN.printf("No\tCommunication Need\tInitial Actor\tFinal Actor");
		writerCN.println();

		banner("Start. Interoperability metamodel conformance evaluation.");
		
		// Load the model
		Model model = (Model) load(URI.createFileURI(outputDir.getAbsolutePath()).appendSegment("modelName").appendFileExtension(UMLResource.FILE_EXTENSION)); // Modify UML model name
		
		banner("Multiplicity rules checks.");
		parsePackagesMultiplicity(model);
		
		out("");
		banner("Interoperability rules checks.");
		out("");
		out("");
		parsePackagesInteroperability(model);
		out("");
		banner("Statistics.");
		out("Number of Communication Needs satisfied = %d", numCNSatisfied);
		out("Number of Sub Communication Needs satisfied = %d", numSubCNTotal);
		out("Number of Actors implicated = %d", actors_stats.size());
		out("Number of Message-Passing Systems implicated = %d", mps_stats.size());
		out("Number of Languages implicated = %d", lang_stats.size());
		out("");
		out("Number of Communication Needs = %d", numCN);
		out("Number of Classes = %d", numClasses);
		out("Total number of Elements/Types = %d", numElements);
		out("");
		banner("End.");
				
		// Save the model
		save(model, URI.createFileURI(outputDir.getAbsolutePath()).appendSegment("savedmodel").appendFileExtension(UMLResource.FILE_EXTENSION));
		
		// Close log text files
		writer.close();
		writerCN.close();
		
		// Display MultiGraph
		if (drawGraph == true)
		{
//			graph.display();
			Viewer viewer = graph.display();	
			Thread.sleep(7000);
			viewer.disableAutoLayout();
		}
	}
	
	
	//
	// Script methods
	//
	
	protected static boolean parsePackagesMultiplicity(org.eclipse.uml2.uml.Package _package)
	{
		boolean correctMultiplicity = true;
		EList<Type> listTypes = _package.getOwnedTypes();
		for (Type type : listTypes)
		{
			if (type instanceof org.eclipse.uml2.uml.Class)
			{
				EList<Stereotype> listStereotypes = type.getAppliedStereotypes();
				if (listStereotypes.size()==1)
				{
					Stereotype stereotype = listStereotypes.get(0);
					switch(stereotype.getName())
					{
					case "Communication Need":
						correctMultiplicity = multiplicityCN((org.eclipse.uml2.uml.Class)type) & correctMultiplicity;
						break;
					case "Message-Passing System":
						correctMultiplicity = multiplicityMPS((org.eclipse.uml2.uml.Class)type) & correctMultiplicity;
						break;
					case "Language Translation":
						correctMultiplicity = multiplicityLT((org.eclipse.uml2.uml.Class)type) & correctMultiplicity;
						break;
					case "Abstract Actor":
						correctMultiplicity = multiplicityAA((org.eclipse.uml2.uml.Class)type) & correctMultiplicity;
						break;
					case "Address":
						correctMultiplicity = multiplicityAddress((org.eclipse.uml2.uml.Class)type) & correctMultiplicity;
						break;
					case "Actor":
						correctMultiplicity = multiplicityActor((org.eclipse.uml2.uml.Class)type) & correctMultiplicity;
						break;
					case "Language":
						break;
					case "Reference Language":
						break;
					}
				}
			}
		}
		EList<Package> listPackages = _package.getNestedPackages();
		for (Package pack : listPackages)
		{
			correctMultiplicity = parsePackagesMultiplicity(pack) & correctMultiplicity;
		}
		return correctMultiplicity;
	}
	
	protected static void parsePackagesInteroperability(org.eclipse.uml2.uml.Package _package)
	{
		EList<Type> listTypes = _package.getOwnedTypes();
		for (Type type : listTypes)
		{
			numElements++;
			if (type instanceof org.eclipse.uml2.uml.Class)
			{
				numClasses++;
				if (type.getAppliedStereotype("RootInteroperability::Communication Need")!=null)
				{
					numCN++;
					communicationNeedSatisfied((org.eclipse.uml2.uml.Class)type);
//					// For debugging communication needs independently
//					if (((org.eclipse.uml2.uml.Class)type).getName().equals("Class Name"))
//					{
//						communicationNeedSatisfied((org.eclipse.uml2.uml.Class)type);
//					}
				}
			}
		}
		EList<Package> listPackages = _package.getNestedPackages();
		for (Package pack : listPackages)
		{
			parsePackagesInteroperability(pack);
		}
	}
	
	// Returns a list with the classes associated to _class given certain stereotypes and/or member ends
	protected static EList<org.eclipse.uml2.uml.Class> getAssociations(org.eclipse.uml2.uml.Class _class, String stereotype, String memberEndCurrent, String memberEndTarget)
	{
		EList<org.eclipse.uml2.uml.Class> classesList = new BasicEList<org.eclipse.uml2.uml.Class>();
		
		EList<Association> listAssociations = _class.getAssociations();
		for (Association association : listAssociations)
		{
			EList<Type> listEndTypes = association.getEndTypes();
			for (Type endType : listEndTypes)
			{
				if ((endType.getAppliedStereotype(stereotype)!=null || stereotype == null) && endType != _class)
				{
					if ((memberEndCurrent == null && memberEndTarget == null) || (memberEndTarget != null && association.getMemberEnd(memberEndTarget, endType) != null) || (memberEndCurrent != null && association.getMemberEnd(memberEndCurrent, _class) != null))
					{
						classesList.add((org.eclipse.uml2.uml.Class) endType);
					}
				}
			}
		}
		return classesList;
	}
	
	
	//
	// Multiplicity checks
	//
	
	protected static boolean multiplicityMPS(org.eclipse.uml2.uml.Class _class)
	{
		int multActor = getAssociations(_class, "RootInteroperability::Actor", null, null).size();
		int multLanguage = getAssociations(_class, "RootInteroperability::Language", null, "format").size();
		if (multActor >= 2 && multLanguage >= 1)
		{
			out("Correct multiplicity for Message-Passing System::%s.", _class.getName());
			return true;
		}
		else
		{
			out("Wrong multiplicity for Message-Passing System::%s.", _class.getName());
			return false;
		}
	}
	
	protected static boolean multiplicityCN(org.eclipse.uml2.uml.Class _class)
	{
		int multActor = getAssociations(_class, "RootInteroperability::Actor", null, null).size(); 
		int	multRL = getAssociations(_class, "RootInteroperability::Reference Language", null, null).size() + getAssociations(_class, "RootInteroperability::Language", null, null).size(); 
		int multCN = getAssociations(_class, "RootInteroperability::Communication Need", "addressingNeed", null).size();
		if (multActor >= 2 && multRL == 1 && multCN <= 1)
		{
			out("Correct multiplicity for Communication Need::%s.", _class.getName());
			return true;
		}
		else
		{
			out("Wrong multiplicity for Communication Need::%s.", _class.getName());
			return false;
		}
	}
	
	protected static boolean multiplicityLT(org.eclipse.uml2.uml.Class _class)
	{
		int multLanguage = getAssociations(_class, "RootInteroperability::Language", null, null).size();
		int multActor = getAssociations(_class, "RootInteroperability::Actor", null, null).size();
		if (multLanguage == 2 && multActor == 1)
		{
			out("Correct multiplicity for Language Translation::%s.", _class.getName());
			return true;
		}
		else
		{
			out("Wrong multiplicity for Language Translation::%s.", _class.getName());
			return false;
		}
	}
	
	protected static boolean multiplicityAA(org.eclipse.uml2.uml.Class _class)
	{
		int multLanguage = getAssociations(_class, "RootInteroperability::Language", null, null).size();
		if (multLanguage >= 1)
		{
			out("Correct multiplicity for Abstract Actor::%s.", _class.getName());
			return true;
		}
		else
		{
			out("Wrong multiplicity for Abstract Actor::%s.", _class.getName());
			return false;
		}
	}
	
	protected static boolean multiplicityAddress(org.eclipse.uml2.uml.Class _class)
	{
		int multActor = getAssociations(_class, "RootInteroperability::Actor", "identifier", null).size();
		if (multActor == 1)
		{
			out("Correct multiplicity for Address::%s.", _class.getName());
			return true;
		}
		else
		{
			out("Wrong multiplicity for Address::%s.", _class.getName());
			return false;
		}
	}
	
	protected static boolean multiplicityActor(org.eclipse.uml2.uml.Class _class)
	{
		int multLanguage = getAssociations(_class, "RootInteroperability::Language", null, null).size();
		if (multLanguage >= 1)
		{
			out("Correct multiplicity for Actor::%s.", _class.getName());
			return true;
		}
		else
		{
			out("Wrong multiplicity for Actor::%s.", _class.getName());
			return false;
		}
	}
	
	
	//
	// Interoperability rules
	//
	
	// Rule 1
	protected static boolean communicationNeedSatisfied(org.eclipse.uml2.uml.Class cn)
	{
		boolean satisfied = false;
		int i = 0, j;
		EList<org.eclipse.uml2.uml.Class> cnActors = getAssociations(cn, "RootInteroperability::Actor", null, null);
		EList<org.eclipse.uml2.uml.Class> emptyList1 = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> emptyList2 = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> act1Languages = new BasicEList<org.eclipse.uml2.uml.Class>();
		
		out("Communication Need: %s", cn.getName());
		out("------------------------------------");
		out("");
		
		for (org.eclipse.uml2.uml.Class act1 : cnActors)
		{
			act1Languages = getAssociations(act1, "RootInteroperability::Language", null, null);
			j = 0;
			for (org.eclipse.uml2.uml.Class act2 : cnActors)
			{
				if (act1 != act2 && i < j) // All the possible combinations given that act1 != act2
				{
					if (isSatisfied(cn, act1, act2, emptyList1, act1Languages, emptyList2))
					{
						cn.setValue(cn.getAppliedStereotype("RootInteroperability::Communication Need"), "satisfied", true);
						out("");
						satisfied = true;
						numCNSatisfied++;
						writerCN.printf("%d & %s & %s & %s \\\\", numCNSatisfied, cn.getName(), act1.getName(), act2.getName());
						writerCN.println();
						writerCN.printf("\\hline");
						writerCN.println();
					}
					else
					{
						out("Communication Need::%s --> Not satisfied", cn.getName());
						out("");
						out("Communication Need not satisfaction analysis:");
						out("");
						notSatisfied(cn, act1, act2);
					}
				}
				j++;
			}
			i++;
		}
		return satisfied;
	}
	
	// Rule 2
	protected static boolean isSatisfied(org.eclipse.uml2.uml.Class cn, org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class targetActor, EList<org.eclipse.uml2.uml.Class> vN, EList<org.eclipse.uml2.uml.Class> allowedLang, EList<org.eclipse.uml2.uml.Class> vNLang)
	{
		boolean satisfied = false;
		EList<org.eclipse.uml2.uml.Class> currActorMPS = getAssociations(currActor, "RootInteroperability::Message-Passing System", null, null);
		EList<org.eclipse.uml2.uml.Class> mpsActor = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> allowedLanguages = new BasicEList<org.eclipse.uml2.uml.Class>();
		
		// Add current actor to the visited nodes if it had not been visited
		if (!vN.contains(currActor))
		{
			vN.add(currActor);
		}
		
		if (currActor == targetActor)
		{
			int j = 0;
			for (int i = 0; i < vN.size() - 2; i = i + 2)
			{
				String languages = null;
				j++;
				if (j < vNLang.size() && vNLang.get(j).getAppliedStereotype("RootInteroperability::Language") != null)
				{
					languages = String.format("%s", vNLang.get(j).getName());
					// For statistics
					if (!lang_stats.contains(vNLang.get(j)))
					{
						lang_stats.add(vNLang.get(j));
					}
				}
				j++;
				while (j < vNLang.size() && vNLang.get(j).getAppliedStereotype("RootInteroperability::Language") != null)
				{
					languages = String.format("%s - %s", languages, vNLang.get(j).getName());
					// For statistics
					if (!lang_stats.contains(vNLang.get(j)))
					{
						lang_stats.add(vNLang.get(j));
					}
					j++;
				}
				out("%s <--> %s <--> %s (%s)", vN.get(i).getName(), vN.get(i+1).getName(), vN.get(i+2).getName(), languages);
				
				// For statistics
				if (!actors_stats.contains(vN.get(i)))
				{
					actors_stats.add(vN.get(i));
				}
				if (!mps_stats.contains(vN.get(i+1)))
				{
					mps_stats.add(vN.get(i+1));
				}
				numSubCNTotal++;
				
				// MultiGraph drawing
				if (flag_language == true && flag_address == true && drawGraph == true)
				{
					if (graph.getNode(vN.get(i).getQualifiedName()) == null)
					{
						Node node = graph.addNode(vN.get(i).getQualifiedName());
						node.addAttribute("ui.label", vN.get(i).getName());
						node.addAttribute("ui.class", "actor");
					}
					if (graph.getNode(vN.get(i+1).getQualifiedName()) == null)
					{
						Node node = graph.addNode(vN.get(i+1).getQualifiedName());
						node.addAttribute("ui.label", vN.get(i+1).getName());
						node.addAttribute("ui.class", "mps");
					}
					if (graph.getNode(vN.get(i+2).getQualifiedName()) == null)
					{
						Node node = graph.addNode(vN.get(i+2).getQualifiedName());
						node.addAttribute("ui.label", vN.get(i+2).getName());
						node.addAttribute("ui.class", "actor");
					}
					String edgeLabel = String.format("#%d", numCNSatisfied+1);
					String edgeId = String.format("%s_%s-%s", cn.getName(), vN.get(i).getName(), vN.get(i+1).getName());
					if (graph.getEdge(edgeId) == null)
					{
						Edge edge = graph.addEdge(edgeId, vN.get(i).getQualifiedName(), vN.get(i+1).getQualifiedName());
						edge.addAttribute("ui.label", edgeLabel);
					}
					edgeId = String.format("%s_%s-%s", cn.getName(), vN.get(i+1).getName(), vN.get(i+2).getName());
					if (graph.getEdge(edgeId) == null)
					{
						Edge edge = graph.addEdge(edgeId, vN.get(i+1).getQualifiedName(), vN.get(i+2).getQualifiedName());
						edge.addAttribute("ui.label", edgeLabel);
					}
				}
			}
			out("");
			return true; // Target actor reached
		}
		else
		{
			// Remove mps which are contained in vN (visited nodes)
			for (org.eclipse.uml2.uml.Class mps : getAssociations(currActor, "RootInteroperability::Message-Passing System", null, null))
			{
				if (vN.contains(mps))
				{
					currActorMPS.remove(mps);
				}
			}
			
			for (org.eclipse.uml2.uml.Class mps : currActorMPS)
			{
				mpsActor = getAssociations(mps, "RootInteroperability::Actor", null, null);
				
				// Remove actors which are contained in vN (visited nodes)
				for (org.eclipse.uml2.uml.Class actor : getAssociations(mps, "RootInteroperability::Actor", null, null))
				{
					if (vN.contains(actor))
					{
						mpsActor.remove(actor);
					}
				}
				
				for (org.eclipse.uml2.uml.Class actor : mpsActor)
				{
					// For debugging
//					out("Current Actor: %s - Next Actor: %s - MPS: %s", currActor.getName(), actor.getName(), mps.getName());
					allowedLanguages = getAllowedLanguages(cn, currActor, actor, mps, allowedLang);
					// For debugging
//					for (org.eclipse.uml2.uml.Class _class : allowedLanguages)
//					{
//						out("Allowed language: %s", _class.getName());
//					}
					if ((allowedLanguages.isEmpty() == false || flag_language == false) && (conditionsForSatisfaction(cn, currActor, actor, mps)))
					{
						// Add MPS and actors to the visited nodes
						if (!vN.contains(mps))
						{
							vN.add(mps);
						}
						if (!vN.contains(actor))
						{
							vN.add(actor);
						}
						// Save allowed languages to the allowed languages visited nodes
						vNLang.add(mps); // To distinguish between allowedLanguages from different actors later
						for (org.eclipse.uml2.uml.Class _class : allowedLanguages)
						{
							vNLang.add(_class);
						}
						
						if (isSatisfied(cn, actor, targetActor, vN, allowedLanguages, vNLang)) // Recursion
						{
							satisfied = true;
						}
						vN.remove(actor);
						vN.remove(vN.lastIndexOf(mps));
						while (vNLang.size() > 0 && vNLang.get(vNLang.size()-1).getAppliedStereotype("RootInteroperability::Language") != null)
						{
							vNLang.remove(vNLang.size()-1); // Remove language
						}
						vNLang.remove(vNLang.size()-1); // Remove mps
					}
				}
			}
		}
		return satisfied;
	}
	
	// Rule 3
	protected static boolean conditionsForSatisfaction(org.eclipse.uml2.uml.Class cn, org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps)
	{
		EList<org.eclipse.uml2.uml.Class> emptyList = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> addressingNeed = getAssociations(cn, "RootInteroperability::Communication Need", null, "addressingNeed");
		if (actorsAreAvailable(currActor, nextActor, mps) && noDistortedMessage(currActor, nextActor, mps) && noDroppedMessage(currActor, nextActor, mps))
		{
			if (addressingSatisfied(currActor, nextActor, mps, emptyList) || flag_address == false)
			{
				return true;
			}
			else
			{
				if (addressingNeed.size() > 0)
				{
					for (org.eclipse.uml2.uml.Class subcn : addressingNeed)
					{
						if (communicationNeedSatisfied(subcn) == false)
						{
							return false;
						}
					}
					return true;
				}
				else
				{
					return false;
				}
			}
		}
		else
		{
			return false;
		}
	}
	
	// Rule 4
	protected static boolean actorsAreAvailable(org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps)
	{
		boolean currActorA = (boolean) currActor.getValue(currActor.getAppliedStereotype("RootInteroperability::Actor"), "isAvailable");
		boolean nextActorA = (boolean) nextActor.getValue(nextActor.getAppliedStereotype("RootInteroperability::Actor"), "isAvailable");
		boolean mpsA = (boolean) mps.getValue(mps.getAppliedStereotype("RootInteroperability::Message-Passing System"), "isAvailable");

		if (currActorA && nextActorA && mpsA)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	// Rule 5
	protected static boolean noDistortedMessage(org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps)
	{
		boolean currActorD = (boolean) currActor.getValue(currActor.getAppliedStereotype("RootInteroperability::Actor"), "distortsMessage");
		boolean nextActorD = (boolean) nextActor.getValue(nextActor.getAppliedStereotype("RootInteroperability::Actor"), "distortsMessage");
		boolean mpsD = (boolean) mps.getValue(mps.getAppliedStereotype("RootInteroperability::Message-Passing System"), "distortsMessage");

		if (currActorD == false && nextActorD == false && mpsD == false)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	// Rule 6
	protected static boolean noDroppedMessage(org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps)
	{
		boolean currActorD = (boolean) currActor.getValue(currActor.getAppliedStereotype("RootInteroperability::Actor"), "dropsMessage");
		boolean nextActorD = (boolean) nextActor.getValue(nextActor.getAppliedStereotype("RootInteroperability::Actor"), "dropsMessage");
		boolean mpsD = (boolean) mps.getValue(mps.getAppliedStereotype("RootInteroperability::Message-Passing System"), "dropsMessage");

		if (currActorD == false && nextActorD == false && mpsD == false)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	// Rule 8
	protected static EList<org.eclipse.uml2.uml.Class> getAllowedLanguages(org.eclipse.uml2.uml.Class cn, org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps, EList<org.eclipse.uml2.uml.Class> allowedLang)
	{
		EList<org.eclipse.uml2.uml.Class> finalAllowedLang = commonLanguages(cn, currActor, nextActor, mps, allowedLang);
		
		// Union
		for (org.eclipse.uml2.uml.Class classLang : translatedLanguages(cn, currActor, nextActor, mps, allowedLang))
		{
			if (!finalAllowedLang.contains(classLang))
			{
				finalAllowedLang.add(classLang);
			}
		}
		return finalAllowedLang;
	}
	
	// Rule 9
	protected static EList<org.eclipse.uml2.uml.Class> commonLanguages(org.eclipse.uml2.uml.Class cn, org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps, EList<org.eclipse.uml2.uml.Class> allowedLang)
	{
		EList<org.eclipse.uml2.uml.Class> commonLang = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> emptyList = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> currActorLang = getAssociations(currActor, "RootInteroperability::Language", null, null);
		EList<org.eclipse.uml2.uml.Class> nextActorLang = getAssociations(nextActor, "RootInteroperability::Language", null, null);
		EList<org.eclipse.uml2.uml.Class> referenceLang = getAssociations(cn, "RootInteroperability::Reference Language", null, null);
		referenceLang.addAll(getAssociations(cn, "RootInteroperability::Language", null, null));
		
		for (org.eclipse.uml2.uml.Class lang : allowedLang)
		{
			if (currActorLang.contains(lang) && nextActorLang.contains(lang) && compatibleWithMPS(lang, mps))
			{
				if (referenceLang.size() == 1)
				{
					emptyList.clear();
					if (getSuperLanguage(referenceLang.get(0), emptyList).contains(lang))
					{
						commonLang.add(lang);
					}
				}
			}
		}
		return commonLang;
	}
	
	// Rule 10
	protected static EList<org.eclipse.uml2.uml.Class> translatedLanguages(org.eclipse.uml2.uml.Class cn, org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps, EList<org.eclipse.uml2.uml.Class> allowedLang)
	{
		EList<org.eclipse.uml2.uml.Class> emptyList = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> superLanguages = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> translatedLanguagesList = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> currActorLang = getAssociations(currActor, "RootInteroperability::Language", null, null);
		EList<org.eclipse.uml2.uml.Class> nextActorLang = getAssociations(nextActor, "RootInteroperability::Language", null, null);
		EList<org.eclipse.uml2.uml.Class> referenceLang = getAssociations(cn, "RootInteroperability::Reference Language", null, null);
		referenceLang.addAll(getAssociations(cn, "RootInteroperability::Language", null, null));

		for (org.eclipse.uml2.uml.Class lang : currActorLang)
		{
			EList<org.eclipse.uml2.uml.Class> langTranslation = getAssociations(lang, "RootInteroperability::Language Translation", null, null);
			for (org.eclipse.uml2.uml.Class langTrans : langTranslation)
			{
				EList<org.eclipse.uml2.uml.Class> actorTrans = getAssociations(langTrans, "RootInteroperability::Actor", null, null);
				EList<org.eclipse.uml2.uml.Class> nextLang = getAssociations(langTrans, "RootInteroperability::Language", null, null);
				boolean correctLT = (boolean) langTrans.getValue(langTrans.getAppliedStereotype("RootInteroperability::Language Translation"), "correct");
				
				for (org.eclipse.uml2.uml.Class nLang : nextLang)
				{
					if (nextActorLang.contains(nLang) && allowedLang.contains(lang) && compatibleWithMPS(nLang, mps) && correctLT && actorTrans.contains(currActor) && lang != nLang)
					{
						if (referenceLang.size() == 1)
						{
							emptyList.clear();
							superLanguages = getSuperLanguage(referenceLang.get(0), emptyList);
							if ((superLanguages.contains(lang)) && (superLanguages.contains(nLang)))
							{
								if (!translatedLanguagesList.contains(nLang))
								{
									translatedLanguagesList.add(nLang);
								}
							}
						}
					}
				}
			}
		}
		return translatedLanguagesList;
	}
	
	// Rule 11
	protected static boolean compatibleWithMPS(org.eclipse.uml2.uml.Class lang, org.eclipse.uml2.uml.Class mps)
	{
		boolean compatible = false;
		EList<org.eclipse.uml2.uml.Class> emptyList = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> mpsLang = getAssociations(mps, "RootInteroperability::Language", null, "format");
		EList<org.eclipse.uml2.uml.Class> carryingLangList = getCarryingLanguage(lang, emptyList);

		for (org.eclipse.uml2.uml.Class carryingLang : carryingLangList)
		{
			if (mpsLang.contains(carryingLang))
			{
				compatible = true;
			}
		}
		if (compatible)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	// Rule 12
	protected static EList<org.eclipse.uml2.uml.Class> getSuperLanguage(org.eclipse.uml2.uml.Class lang, EList<org.eclipse.uml2.uml.Class> vN)
	{
		EList<org.eclipse.uml2.uml.Class> allSuperLang = new BasicEList<org.eclipse.uml2.uml.Class>(); // List that contains all the super languages of lang
		EList<org.eclipse.uml2.uml.Class> superLang = getAssociations(lang, null, null, "superLanguage"); // List that contains the direct super languages of lang
		EList<org.eclipse.uml2.uml.Class> superLangAux = new BasicEList<org.eclipse.uml2.uml.Class>();
		superLangAux.addAll(superLang);
		
		if (!vN.contains(lang))
		{
			vN.add(lang);
		}
		
		// Remove languages which are contained in vN (visited nodes)
		for (org.eclipse.uml2.uml.Class classLang : superLang)
		{
			if (vN.contains(classLang))
			{
				superLangAux.remove(classLang);
			}
		}
		
		// Union language
		if (!allSuperLang.contains(lang))
		{
			allSuperLang.add(lang);
		}
				
		// Union superLanguage
		for (org.eclipse.uml2.uml.Class classLang : superLang)
		{
			if (!allSuperLang.contains(classLang))
			{
				allSuperLang.add(classLang);
			}
		}
		
		// Find nodes of the previous super languages
		for (org.eclipse.uml2.uml.Class classLang : superLangAux)
		{
			if (!vN.contains(classLang))
			{
				vN.add(classLang);
				for (org.eclipse.uml2.uml.Class classLang1 : getSuperLanguage(classLang, vN)) // Recursion
				{
					if (!allSuperLang.contains(classLang1))
					{
						allSuperLang.add(classLang1);
					}
				}
			}
		}
		return allSuperLang;
	}
	
	// Rule 13
	protected static EList<org.eclipse.uml2.uml.Class> getCarryingLanguage(org.eclipse.uml2.uml.Class lang, EList<org.eclipse.uml2.uml.Class> vN)
	{
		EList<org.eclipse.uml2.uml.Class> allCarryingLang = new BasicEList<org.eclipse.uml2.uml.Class>(); // List that contains all the carrying languages of lang
		EList<org.eclipse.uml2.uml.Class> carryingLang = getAssociations(lang, null, null, "carryingLanguage"); // List that contains the direct carrying languages of lang
		EList<org.eclipse.uml2.uml.Class> carryingLangAux = new BasicEList<org.eclipse.uml2.uml.Class>();
		carryingLangAux.addAll(carryingLang);
		
		if (!vN.contains(lang))
		{
			vN.add(lang);
		}
		
		// Remove languages which are contained in vN (visited nodes)
		for (org.eclipse.uml2.uml.Class classLang : carryingLang)
		{
			if (vN.contains(classLang))
			{
				carryingLangAux.remove(classLang);
			}
		}
		
		// Union language
		if (!allCarryingLang.contains(lang))
		{
			allCarryingLang.add(lang);
		}
		
		// Union carryingLanguage
		for (org.eclipse.uml2.uml.Class classLang : carryingLang)
		{
			if (!allCarryingLang.contains(classLang))
			{
				allCarryingLang.add(classLang);
			}
		}
				
		// Find nodes of the previous carrying languages
		for (org.eclipse.uml2.uml.Class classLang : carryingLangAux)
		{
			if (!vN.contains(classLang))
			{
				vN.add(classLang);
				for (org.eclipse.uml2.uml.Class classLang1 : getCarryingLanguage(classLang, vN)) // Recursion
				{
					if (!allCarryingLang.contains(classLang1))
					{
						allCarryingLang.add(classLang1);
					}
				}
			}
		}
		
		return allCarryingLang;
	}
	
	// Rule 14
	protected static boolean addressingSatisfied(org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps, EList<org.eclipse.uml2.uml.Class> vN)
	{
		if (fixedMPS(mps) || addressable(currActor, nextActor, mps, vN))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	
	// Rule 15
	protected static boolean fixedMPS(org.eclipse.uml2.uml.Class mps)
	{
		return (boolean) mps.getValue(mps.getAppliedStereotype("RootInteroperability::Message-Passing System"), "fixed");
	}
	
	// Rule 16
	protected static boolean addressable(org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps, EList<org.eclipse.uml2.uml.Class> vN)
	{
		EList<org.eclipse.uml2.uml.Class> currActorKnownAddress = getAssociations(currActor, "RootInteroperability::Address", null, "knownAddress");
		EList<org.eclipse.uml2.uml.Class> nextActorKnownAddress = getAssociations(nextActor, "RootInteroperability::Address", null, "knownAddress");
		EList<org.eclipse.uml2.uml.Class> currActorOwnAddress = getAssociations(currActor, "RootInteroperability::Address", null, "identifier");
		EList<org.eclipse.uml2.uml.Class> nextActorOwnAddress = getAssociations(nextActor, "RootInteroperability::Address", null, "identifier");
		
		for (org.eclipse.uml2.uml.Class address : currActorKnownAddress)
		{
			if (nextActorOwnAddress.contains(address) && (validAddressOnMPS(address, mps) || addressTranslatorExists(currActor, nextActor, mps, vN)))
			{
				return true;
			}
		}
		for (org.eclipse.uml2.uml.Class address : nextActorKnownAddress)
		{
			if (currActorOwnAddress.contains(address) && (validAddressOnMPS(address, mps) || addressTranslatorExists(currActor, nextActor, mps, vN)))
			{
				return true;
			}
		}
		return false;
	}
	
	// Rule 17
	protected static boolean validAddressOnMPS(org.eclipse.uml2.uml.Class address, org.eclipse.uml2.uml.Class mps)
	{
		EList<org.eclipse.uml2.uml.Class> addressLang = getAssociations(address, "RootInteroperability::Language", null, null);
		
		for (org.eclipse.uml2.uml.Class language : getAssociations(mps, "RootInteroperability::Language", null, "addressingLanguage"))
		{
			if (addressLang.contains(language))
			{
				return true;
			}
		}
		return false;
	}
	
	// Rule 18
	protected static boolean addressTranslatorExists(org.eclipse.uml2.uml.Class currActor, org.eclipse.uml2.uml.Class nextActor, org.eclipse.uml2.uml.Class mps, EList<org.eclipse.uml2.uml.Class> vN)
	{
		EList<org.eclipse.uml2.uml.Class> mpsActors = getAssociations(mps, "RootInteroperability::Actor", null, null);
		
		EList<org.eclipse.uml2.uml.Class> currActorKnownAddressL = new BasicEList<org.eclipse.uml2.uml.Class>();
		for (org.eclipse.uml2.uml.Class address : getAssociations(currActor, "RootInteroperability::Address", null, "knownAddress"))
		{
			for (org.eclipse.uml2.uml.Class lang : getAssociations(address, "RootInteroperability::Language", null, null))
			{
				currActorKnownAddressL.add(lang);
			}
		}
		
		EList<org.eclipse.uml2.uml.Class> nextActorKnownAddressL = new BasicEList<org.eclipse.uml2.uml.Class>();
		for (org.eclipse.uml2.uml.Class address : getAssociations(nextActor, "RootInteroperability::Address", null, "knownAddress"))
		{
			for (org.eclipse.uml2.uml.Class lang : getAssociations(address, "RootInteroperability::Language", null, null))
			{
				nextActorKnownAddressL.add(lang);
			}
		}
		
		EList<org.eclipse.uml2.uml.Class> currActorOwnAddressL = new BasicEList<org.eclipse.uml2.uml.Class>();
		for (org.eclipse.uml2.uml.Class address : getAssociations(currActor, "RootInteroperability::Address", null, "identifier"))
		{
			for (org.eclipse.uml2.uml.Class lang : getAssociations(address, "RootInteroperability::Language", null, null))
			{
				currActorOwnAddressL.add(lang);
			}
		}
		
		EList<org.eclipse.uml2.uml.Class> nextActorOwnAddressL = new BasicEList<org.eclipse.uml2.uml.Class>();
		for (org.eclipse.uml2.uml.Class address : getAssociations(nextActor, "RootInteroperability::Address", null, "identifier"))
		{
			for (org.eclipse.uml2.uml.Class lang : getAssociations(address, "RootInteroperability::Language", null, null))
			{
				nextActorOwnAddressL.add(lang);
			}
		}
		
		// Remove actors which are contained in vN (visited nodes)
		for (org.eclipse.uml2.uml.Class actor : getAssociations(mps, "RootInteroperability::Actor", null, null))
		{
			if (vN.contains(actor))
			{
				mpsActors.remove(actor);
			}
		}
		
		for (org.eclipse.uml2.uml.Class actor : mpsActors)
		{
			for (org.eclipse.uml2.uml.Class transLang : getAssociations(actor, "RootInteroperability::Language", null, null))
			{
				if (((currActorOwnAddressL.contains(transLang) && nextActorKnownAddressL.contains(transLang)) 
				|| (nextActorOwnAddressL.contains(transLang) && currActorKnownAddressL.contains(transLang))))
				{
					vN.add(actor);
					if (addressingSatisfied(currActor, actor, mps, vN) && addressingSatisfied(actor, nextActor, mps, vN))
					{
						return true;
					}
				}
			}
		}
		return false;
	}
	
	
	// Communication need not satisfied (reasons)
	protected static void notSatisfied(org.eclipse.uml2.uml.Class cn, org.eclipse.uml2.uml.Class act1, org.eclipse.uml2.uml.Class act2)
	{
		EList<org.eclipse.uml2.uml.Class> emptyList1 = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> emptyList2 = new BasicEList<org.eclipse.uml2.uml.Class>();
		EList<org.eclipse.uml2.uml.Class> act1Languages = getAssociations(act1, "RootInteroperability::Language", null, null);
		EList<org.eclipse.uml2.uml.Class> act1LanguagesAux = new BasicEList<org.eclipse.uml2.uml.Class>();
		act1LanguagesAux.addAll(act1Languages);
		
		flag_language = false;
		flag_address = false;
		if (isSatisfied(cn, act1, act2, emptyList1, act1Languages, emptyList2))
		{
			out("Ok: Path for the communication to be possible.");
			out("");
			flag_language = false;
			flag_address = true;
			emptyList1.clear();
			emptyList2.clear();
			act1Languages.clear();
			act1Languages.addAll(act1LanguagesAux);
			if (isSatisfied(cn, act1, act2, emptyList1, act1Languages, emptyList2))
			{
				out("Ok: Addressing satisfied.");
				out("Problem: No allowed languages.");
				out("");
				out("");
			}
			else
			{
				flag_language = true;
				flag_address = false;
				emptyList1.clear();
				emptyList2.clear();
				act1Languages.clear();
				act1Languages.addAll(act1LanguagesAux);
				if (isSatisfied(cn, act1, act2, emptyList1, act1Languages, emptyList2))
				{
					out("Ok: Allowed languages.");
					out("Problem: Addressing not satisfied.");
					out("");
					out("");
				}
				else
				{
					out("Problem: No allowed languages.");
					out("Problem: Addressing not satisfied.");
					out("");
					out("");
				}
			}
		}
		else
		{
			out("Problem: No path for the communication to be possible.");
			out("");
			out("");
		}
	flag_language = true;
	flag_address = true;
	}
	

	//
	// Program control
	//

	private static boolean processArgs(String[] args)
	
	throws IOException 
	{
		if (args.length != 1) 
		{
			err("Expected 1 argument.");
			err("Usage: java -jar ... %s <dir>", interoperability.class.getSimpleName());
			err("where");
			err("<dir> - path to output folder in which to save the UML profile");
			return false;
		}
		outputDir = new File(args[0]).getCanonicalFile();
		if (!outputDir.exists()) 
		{
			err("No such directory: %s", outputDir.getAbsolutePath());
			return false;
		}
		if (!outputDir.isDirectory()) 
		{
			err("Not a directory: %s", outputDir.getAbsolutePath());
			return false;
		}
		if (!outputDir.canWrite()) 
		{
			err("Cannot create a file in directory: %s", outputDir.getAbsolutePath());
			return false;
		}
		return true;
	}

	protected static void save(org.eclipse.uml2.uml.Package package_, URI uri) 
	{
		// Modified by PabloAG
		Resource resource = package_.eResource();
		resource.setURI(uri);
		try 
		{
			resource.save(null);
		} 
		catch (IOException ioe) 
		{
			err(ioe.getMessage());
		}
	}
	
	protected static org.eclipse.uml2.uml.Package load(URI uri) 
	{
		org.eclipse.uml2.uml.Package package_ = null;
		try 
		{
			// Load the requested resource
			Resource resource = RESOURCE_SET.getResource(uri, true);

			// Get the first (should be only) package from it
			package_ = (org.eclipse.uml2.uml.Package) EcoreUtil.getObjectByType(resource.getContents(), UMLPackage.Literals.PACKAGE);
		} 
		catch (WrappedException we) 
		{
			err(we.getMessage());
			System.exit(1);
		}
		return package_;
	}

	
	//
	// Logging utilities
	//

	protected static void banner(String format, Object... args) 
	{
		System.out.println();
		hrule();
		System.out.printf(format, args);
		if (!format.endsWith("%n")) 
		{
			System.out.println();
		}
		hrule();
		System.out.println();
		
		writer.println();
		writer.println("------------------------------------");
		writer.printf(format, args);
		if (!format.endsWith("%n")) 
		{
			writer.println();
		}
		writer.println("------------------------------------");
		writer.println();
	}

	protected static void hrule() 
	{
		System.out.println("------------------------------------");
	}

	protected static void out(String format, Object... args)
	{
		if (DEBUG) 
		{
			System.out.printf(format, args);
			writer.printf(format, args);
			if (!format.endsWith("%n")) 
			{
				System.out.println();
				writer.println();
			}
		}
	}

	protected static void err(String format, Object... args) 
	{
		System.err.printf(format, args);
		writer.printf(format, args);
		if (!format.endsWith("%n")) 
		{
			System.err.println();
			writer.println();
		}
	}
}