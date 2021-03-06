package metagene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import nextgen.core.annotation.Gene;

/**
 * @author prussell
 *
 */
public class GeneComponent3UTR extends AbstractGeneComponent {
	
	private static String NAME = "3UTR";
	private int minSize;
	private Collection<GeneComponent> requiredComponents;

	/**
	 * 
	 */
	public GeneComponent3UTR() {
		this(null, 0);
	}
	
	/**
	 * @param minUtrSize Minimum UTR size
	 */
	public GeneComponent3UTR(int minUtrSize) {
		this(null, minUtrSize);
	}
	
	/**
	 * @param requiredGeneComponents Components that gene must have in order to report a 3UTR
	 */
	public GeneComponent3UTR(Collection<GeneComponent> requiredGeneComponents) {
		this(requiredGeneComponents, 0);
	}
	
	/**
	 * @param requiredGeneComponents Components that gene must have in order to report a 3UTR
	 * @param minUtrSize Minimum UTR size
	 */
	public GeneComponent3UTR(Collection<GeneComponent> requiredGeneComponents, int minUtrSize) {
		minSize = minUtrSize;
		requiredComponents = new ArrayList<GeneComponent>();
		if(requiredGeneComponents != null) {
			requiredComponents.addAll(requiredGeneComponents);
		}
	}


	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public Collection<Gene> getComponent(Gene gene) {
		if(!gene.hasCDS()) {
			return null;
		}
		Gene utr = gene.get3UTRGene();
		if(utr == null) {
			return null;
		}
		if(utr.size() < minSize) {
			return null;
		}
		for(GeneComponent requirement : requiredComponents) {
			if(requirement.getComponent(gene) == null) {
				return null;
			}
		}
		Collection<Gene> rtrn = new TreeSet<Gene>();
		rtrn.add(utr);
		return rtrn;
	}


	@Override
	public boolean reverseDataIfMinusOrientation() {
		return true;
	}

}
