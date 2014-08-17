package edu.cuny.qc.util.fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.collections15.BidiMap;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.bidimap.DualHashBidiMap;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.uimafit.util.JCasUtil;

import ac.biu.nlp.nlp.ace_uima.AceAbnormalMessage;
import ac.biu.nlp.nlp.ace_uima.AceException;
import ac.biu.nlp.nlp.ace_uima.uima.EventMentionArgument;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import edu.cuny.qc.util.Utils;
import edu.cuny.qc.util.fragment.TreeFragmentBuilder.TreeFragmentBuilderException;
import eu.excitementproject.eop.common.datastructures.OneToManyBidiMultiHashMap;
import eu.excitementproject.eop.common.representation.parse.representation.basic.InfoGetFields;
import eu.excitementproject.eop.common.representation.parse.tree.TreeAndParentMap.TreeAndParentMapException;
import eu.excitementproject.eop.common.representation.parse.tree.dependency.basic.BasicNode;
import eu.excitementproject.eop.common.representation.partofspeech.UnsupportedPosTagStringException;

public class FragmentLayer {
	public JCas jcas;
	public Map<Token, Collection<Sentence>> tokenIndex;	
	public OneToManyBidiMultiHashMap<Token, BasicNode> token2nodes;
	public BidiMap<Sentence, BasicNode> sentence2root;
	public TreeFragmentBuilder fragmenter;
	public Map<BasicNode, Facet> linkToFacet;
	
	public FragmentLayer(JCas jcas, CasTreeConverter converter) throws FragmentLayerException {
		try {
			this.jcas = jcas;
			tokenIndex = JCasUtil.indexCovering(jcas, Token.class, Sentence.class);
			
			token2nodes = new OneToManyBidiMultiHashMap<Token,BasicNode>();
			sentence2root = new DualHashBidiMap<Sentence, BasicNode>();
			
			boolean errors = false;
			for (Sentence sentence : JCasUtil.select(jcas, Sentence.class)) {
				try {
					BasicNode root = converter.convertSingleSentenceToTree(jcas, sentence);
					/// DEBUG
//					if (sentence.getCoveredText().contains("esterday's")) {
//						System.out.printf("");
//					}
					///
					token2nodes.putAll(converter.getAllTokensToNodes());
					sentence2root.put(sentence, root);
				}
				catch (CasTreeConverterException e) {
					System.err.printf("\n- Got error while working on sentence: '%s'\n", sentence.getCoveredText());
					e.printStackTrace(System.err);
					System.err.printf("================\n\n");
					errors = true;
				}
			}
			
			if (errors) {
				throw new FragmentLayerException("got errors while converting CAS (detailed before) - aborting."); 
			}
			
			//converter.convertCasToTrees(jcas);
			//token2nodes = converter.getAllTokensToNodes();
			//sentence2root = converter.getSentenceToRootMap();
			
			fragmenter = new TreeFragmentBuilder();
			linkToFacet = new LinkedHashMap<BasicNode, Facet>();
		}
		catch (UnsupportedPosTagStringException e) {
			throw new FragmentLayerException(e);
		}
	}
	
	public List<BasicNode> getTreeFragments(Annotation covering) throws CASException, AceException, TreeAndParentMapException, TreeFragmentBuilderException {
		List<BasicNode> result = new ArrayList<BasicNode>();
		if (covering != null) {
			MultiMap<Sentence, Token> sentence2tokens = Utils.getCoveringSentences(covering, tokenIndex);
			for (Entry<Sentence,Collection<Token>> entry : sentence2tokens.entrySet()) {
				FragmentAndReference frag = getFragmentBySentenceAndTokens(entry.getKey(), entry.getValue(), null);
				result.add(frag.getFragmentRoot());
			}
		}
		return result;
	}

	public FragmentAndReference getFragmentBySentenceAndTokens(Sentence sentence, Collection<Token> tokens, Facet facet) throws TreeAndParentMapException, TreeFragmentBuilderException {
		BasicNode root = sentence2root.get(sentence);
		Set<BasicNode> targetNodes = new LinkedHashSet<BasicNode>(tokens.size());
		for (Token token : tokens) {
			Collection<BasicNode> nodes = token2nodes.get(token);
			/// DEBUG
			if (nodes == null) {
				System.out.printf("");
			}
			///
			targetNodes.addAll(nodes); //Also get duplicated nodes!
		}
		//logger.trace("-------- fragmenter.build(" + AnotherBasicNodeUtils.getNodeString(root) + ", " + AnotherBasicNodeUtils.getNodesString(targetNodes) + ")");
		FragmentAndReference fragRef = fragmenter.build(root, targetNodes, facet);
		return fragRef;
	}
	
	/**
	 * Returns a tree fragment of the connection between the roots of the two covering annotations.<BR><BR>
	 * This method assumes that each covering annotation is within sentence boundaries,
	 * otherwise it doesn't make much sense. This is in contrary to {@link #getTreeFragments(Annotation)}
	 * which does not assume that and may return multiple fragments.
	 * @param covering
	 * @return
	 * @throws CASException
	 * @throws AceException
	 * @throws TreeAndParentMapException
	 * @throws TreeFragmentBuilderException
	 * @throws AceAbnormalMessage 
	 */
	public FragmentAndReference getRootLinkingTreeFragment(Annotation /*EventMentionAnchor*/ eventAnchor, Annotation /*BasicArgumentMentionHead*/ argHead, Object /*EventMentionArgument*/ argMention) throws CASException, AceException, TreeAndParentMapException, TreeFragmentBuilderException, AceAbnormalMessage {
		if (eventAnchor == null || argHead == null) {
			throw new AceAbnormalMessage("NullParam");
		}
		
		//logger.trace("%%% 1");
		
		MultiMap<Sentence, Token> sentence2tokens_1 = Utils.getCoveringSentences(eventAnchor, tokenIndex);
		MultiMap<Sentence, Token> sentence2tokens_2 = Utils.getCoveringSentences(argHead, tokenIndex);
		if (sentence2tokens_1.size() == 0 || sentence2tokens_2.size() == 0) {
			throw new AceAbnormalMessage(String.format("ERR:No Covering Sentence for trigger '%s' or arg '%s' (or both)", eventAnchor.getCoveredText(), argHead.getCoveredText()));
//			throw new AceAbnormalMessage("ERR:No Covering Sentence"
//					//, String.format("Got at least one of the two annotations, that is not covered by any sentence: " +
//					//"(%s sentences, %s sentences)", sentence2tokens_1.size(), sentence2tokens_2.size()), logger
//					);
		}
		if (sentence2tokens_1.size() > 1 || sentence2tokens_2.size() > 1) {
			throw new AceAbnormalMessage("ERR:Multiple Sentence Annotation", String.format("Got at least one of the two annotations, that does not cover exactly one sentence: " +
					"(%s sentences, %s sentences)", sentence2tokens_1.size(), sentence2tokens_2.size()), null);
		}
		Entry<Sentence,Collection<Token>> s2t1 = sentence2tokens_1.entrySet().iterator().next();
		Entry<Sentence,Collection<Token>> s2t2 = sentence2tokens_2.entrySet().iterator().next();
		if (s2t1.getKey() != s2t2.getKey()) {
			throw new AceAbnormalMessage("ERR:Different Sentences", String.format("Got two annotations in different sentences: sentence1=%s, sentence2=%s",
					s2t1.getKey(), s2t2.getKey()), null);
		}
		Sentence sentence = s2t1.getKey();

		//logger.trace("%%% 2");

		// get the fragment of each covering annotation
		FragmentAndReference frag1 = getFragmentBySentenceAndTokens(sentence, s2t1.getValue(), null);
		FragmentAndReference frag2 = getFragmentBySentenceAndTokens(sentence, s2t2.getValue(), null);
		//logger.trace("%%% 3");

		// and now... get the fragment containing the roots of both fragments!
		// this is the connecting fragment
		Token root1 = token2nodes.getSingleKeyOf(frag1.getOrigReference());
		Token root2 = token2nodes.getSingleKeyOf(frag2.getOrigReference());
//		Token root1 = info2token.get(frag1.getInfo());
//		Token root2 = info2token.get(frag2.getInfo());
		//logger.trace("%%% 4");

		//TODO remove, for debug
//		List<BasicNode> n = new ArrayList<BasicNode>();
//		for (BasicNode nn : token2nodes.values()) {
//			if (frag1.getInfo().getNodeInfo().getWord().equals(nn.getInfo().getNodeInfo().getWord())) {
//				n.add(nn);
//			}
//		}
		//TODO finish
		
		Facet facet = new Facet(frag1.getOrigReference(), frag2.getOrigReference(), eventAnchor, (EventMentionArgument) argMention, sentence);

		List<Token> bothRoots = Arrays.asList(new Token[] {root1, root2});
		FragmentAndReference connectingFrag = getFragmentBySentenceAndTokens(sentence, bothRoots, facet);
		//logger.trace("%%% 5");

		linkToFacet.put(connectingFrag.getFragmentRoot(), facet);
		return connectingFrag;
	}
	
	public static String getTreeout(BasicNode tree, SimpleNodeString nodeStr) {
		return TreePrinter.getString(tree, "( ", " )", nodeStr);
	}
	
	public static String getTreeoutDependenciesTokens(BasicNode tree) {
		return getTreeout(tree, new SimpleNodeString() {
			@Override public String toString(BasicNode node) {
				return InfoGetFields.getRelation(node.getInfo(), "<ROOT>")+"->"+InfoGetFields.getWord(node.getInfo());
			}
		});
	}
	
	public static String getTreeout(List<BasicNode> trees, boolean withContext, SimpleNodeString nodeStr) {
		if (trees.isEmpty()) {
			return "(empty-tree)";
		}
		String subrootDep = null;
		if (!withContext) {
			subrootDep = "<SUBROOT>";
		}
		return TreePrinter.getString(trees, "( ", " )", "#", subrootDep, nodeStr);
	}
	
	public static String getTreeoutOnlyDependencies(List<BasicNode> trees, boolean withContext) {
		return getTreeout(trees, withContext, new SimpleNodeString() {
			@Override public String toString(BasicNode node) {
				return " "+InfoGetFields.getRelation(node.getInfo(), "<ROOT>")+" ";
			}
		});
	}

	public static String getTreeoutDependenciesToken(List<BasicNode> trees, boolean withContext) {
		return getTreeout(trees, withContext, new SimpleNodeString() {
			@Override public String toString(BasicNode node) {
				return " "+InfoGetFields.getRelation(node.getInfo(), "<ROOT>")+"->"+InfoGetFields.getWord(node.getInfo())+" ";
			}
		});
	}

	public static String getTreeoutDependenciesSpecificPOS(List<BasicNode> trees, boolean withContext) {
		return getTreeout(trees, withContext, new SimpleNodeString() {
			@Override public String toString(BasicNode node) {
				return " "+InfoGetFields.getRelation(node.getInfo(), "<ROOT>")+"->"+InfoGetFields.getPartOfSpeech(node.getInfo())+" ";
			}
		});
	}

	public static String getTreeoutDependenciesGeneralPOS(List<BasicNode> trees, boolean withContext) {
		return getTreeout(trees, withContext, new SimpleNodeString() {
			@Override public String toString(BasicNode node) {
				return " "+InfoGetFields.getRelation(node.getInfo(), "<ROOT>")+"->"+node.getInfo().getNodeInfo().getSyntacticInfo().getPartOfSpeech().getCanonicalPosTag()+" ";
			}
		});
	}

}