package edu.cuny.qc.scorer.mechanism;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import edu.cuny.qc.scorer.Aggregator;
import edu.cuny.qc.scorer.ScorerData;
import edu.cuny.qc.scorer.SignalMechanism;
import edu.cuny.qc.scorer.SignalMechanismException;
import edu.cuny.qc.scorer.SignalMechanismSpecTokenIterator;
import edu.cuny.qc.util.BrownClusters;
import eu.excitementproject.eop.common.representation.partofspeech.PartOfSpeech;

public class BrownClustersSignalMechanism extends SignalMechanism {

	static {
		System.err.println("BrownClustersSignalMechanism: should still add the REAL (probably) method, probably something like making sure that one side's bit string is just a substring of the other.");
		System.err.println("BrownClustersSignalMechanism: Sometimes 'null' is returned by BrownClusters.getSingleton().getBrownCluster(str). WTF?");
	}
	
	@Override
	public void addScorers() {
		addTrigger(new ScorerData("BR_ALL_CLUSTERS_TOK",		SameAllClustersToken.inst,				Aggregator.Any.inst		));
		addTrigger(new ScorerData("BR_ALL_CLUSTERS_LEM",		SameAllClustersLemma.inst,				Aggregator.Any.inst		));
		addTrigger(new ScorerData("BR_LONGEST_CLUSTER_TOK",		SameLongestClusterToken.inst,			Aggregator.Any.inst		));
		addTrigger(new ScorerData("BR_LONGEST_CLUSTER_LEM",		SameLongestClusterLemma.inst,			Aggregator.Any.inst		));
		addTrigger(new ScorerData("BR_ALL_CLUSTERS_TOK",		SameAllClustersToken.inst,				Aggregator.Min2.inst		));
		addTrigger(new ScorerData("BR_ALL_CLUSTERS_LEM",		SameAllClustersLemma.inst,				Aggregator.Min2.inst		));
		addTrigger(new ScorerData("BR_LONGEST_CLUSTER_TOK",		SameLongestClusterToken.inst,			Aggregator.Min2.inst		));
		addTrigger(new ScorerData("BR_LONGEST_CLUSTER_LEM",		SameLongestClusterLemma.inst,			Aggregator.Min2.inst		));
	}

	public BrownClustersSignalMechanism() throws SignalMechanismException {
		super();
	}

	private static abstract class BrownClustersScorer extends SignalMechanismSpecTokenIterator {
		/**
		 * Work on surface form, not lemma
		 */
		@Override
		public String getForm(Token token) {
			return token.getCoveredText();
		}
	}
	private static class SameAllClustersToken extends BrownClustersScorer {
		public static final SameAllClustersToken inst = new SameAllClustersToken();
		@Override public String getForm(Token token) { return token.getCoveredText();}
		@Override
		public Boolean calcTokenBooleanScore(Token textToken, Map<Class<?>, Object> textTriggerTokenMap, String textStr, PartOfSpeech textPos, String specStr, PartOfSpeech specPos) throws SignalMechanismException
		{
			List<String> textClusters = getBrownCluster(textStr);
			List<String> specClusters = getBrownCluster(specStr);
			if (textClusters == null || specClusters == null) {
				return false;
			}
			return textClusters.equals(specClusters);
		}
	}

	private static class SameAllClustersLemma extends BrownClustersScorer {
		public static final SameAllClustersToken inst = new SameAllClustersToken();
		@Override
		public Boolean calcTokenBooleanScore(Token textToken, Map<Class<?>, Object> textTriggerTokenMap, String textStr, PartOfSpeech textPos, String specStr, PartOfSpeech specPos) throws SignalMechanismException
		{
			List<String> textClusters = getBrownCluster(textStr);
			List<String> specClusters = getBrownCluster(specStr);
			if (textClusters == null || specClusters == null) {
				return false;
			}
			return textClusters.equals(specClusters);
		}
	}

	private static class SameLongestClusterToken extends BrownClustersScorer {
		public static final SameLongestClusterToken inst = new SameLongestClusterToken();
		@Override public String getForm(Token token) { return token.getCoveredText();}
		@Override
		public Boolean calcTokenBooleanScore(Token textToken, Map<Class<?>, Object> textTriggerTokenMap, String textStr, PartOfSpeech textPos, String specStr, PartOfSpeech specPos) throws SignalMechanismException
		{
			List<String> textClusters = getBrownCluster(textStr);
			List<String> specClusters = getBrownCluster(specStr);
			if (textClusters == null || specClusters == null) {
				return false;
			}
			String textLongestCluster = textClusters.get(textClusters.size()-1);
			String specLongestCluster = specClusters.get(specClusters.size()-1);
			return textLongestCluster.equals(specLongestCluster);
		}
	}

	private static class SameLongestClusterLemma extends BrownClustersScorer {
		public static final SameLongestClusterLemma inst = new SameLongestClusterLemma();
		@Override
		public Boolean calcTokenBooleanScore(Token textToken, Map<Class<?>, Object> textTriggerTokenMap, String textStr, PartOfSpeech textPos, String specStr, PartOfSpeech specPos) throws SignalMechanismException
		{
			List<String> textClusters = getBrownCluster(textStr);
			List<String> specClusters = getBrownCluster(specStr);
			if (textClusters == null || specClusters == null) {
				return false;
			}
			String textLongestCluster = textClusters.get(textClusters.size()-1);
			String specLongestCluster = specClusters.get(specClusters.size()-1);
			return textLongestCluster.equals(specLongestCluster);
		}
	}

	
	private static List<String> getBrownCluster(String str) {
		if (cacheCluster.containsKey(str)) {
			return cacheCluster.get(str);
		}
		else {
			List<String> result = BrownClusters.getSingleton().getBrownCluster(str);
			if (result == null) {
				//System.err.printf("BrownClustersSignalMechanism: cluster==null for: '%s'\n", str);
			}
			cacheCluster.put(str, result);
			return result;
		}
	}
	
	private static Map<String, List<String>> cacheCluster = new LinkedHashMap<String, List<String>>();
}
