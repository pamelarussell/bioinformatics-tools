package translation;

import guttmanlab.core.annotation.Annotation;
import guttmanlab.core.annotation.Annotation.Strand;
import guttmanlab.core.annotation.Gene;
import guttmanlab.core.annotation.SingleInterval;
import guttmanlab.core.annotation.io.BEDFileIO;
import guttmanlab.core.annotationcollection.AnnotationCollection;
import guttmanlab.core.annotationcollection.FeatureCollection;
import guttmanlab.core.coordinatespace.CoordinateSpace;
import guttmanlab.core.sequence.FastaFileIOImpl;
import guttmanlab.core.sequence.Sequence;
import guttmanlab.core.util.CommandLineParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

public class ORFFinder {
	
	private Map<String, Sequence> chrsByName;
	private CoordinateSpace coordSpace;
	private String referenceSizeFile;
	
	private static Logger logger = Logger.getLogger(ORFFinder.class.getName());
	public static final Pattern START_CODON= Pattern.compile("ATG",  Pattern.CASE_INSENSITIVE);
	public static final String [] STOP_CODONS =  {"TAG", "TAA", "TGA"};
	
	public ORFFinder(String genomeFasta, String chrSizeFile) {
		referenceSizeFile = chrSizeFile;
		coordSpace = new CoordinateSpace(referenceSizeFile);
		chrsByName = new FastaFileIOImpl().readFromFileByName(genomeFasta);
	}
	
	/**
	 * Get all possible open reading frames of a gene
	 * @param gene The gene
	 * @return Collection of open reading frames
	 */
	public AnnotationCollection<Gene> getAllORFs(Gene gene) {
		if(!gene.getOrientation().equals(Strand.POSITIVE) && !gene.getOrientation().equals(Strand.NEGATIVE)) {
			throw new IllegalArgumentException("Gene strand must be known.");
		}
		Sequence chr = chrsByName.get(gene.getReferenceName());
		if(chr == null) {
			String chrNames = "";
			for(String c : chrsByName.keySet()) {
				chrNames += c = " ";
			}
			throw new IllegalArgumentException("Can't get ORFs for gene " + gene.getName() + " because reference " + gene.getReferenceName() + " is missing. Sequence names: " + chrNames);
		}
		Sequence geneSeq = chr.getSubsequence(gene);
		Collection<int[]> orfCoords = findAllORFs(geneSeq);
		FeatureCollection<Gene> rtrn = new FeatureCollection<Gene>(coordSpace);
		for(int[] coord : orfCoords) {
			Gene featureSpaceInterval = new Gene(new SingleInterval(gene.getName(), coord[0], coord[1], Strand.POSITIVE));
			Annotation referenceSpaceCDS = gene.convertToReferenceSpace(featureSpaceInterval);
			int cdsStart = referenceSpaceCDS.getReferenceStartPosition();
			int cdsEnd = referenceSpaceCDS.getReferenceEndPosition();
			String orfName = gene.getName() + ":ORF:" + gene.getReferenceName() + ":" + cdsStart + "-" + cdsEnd + ":" + gene.getOrientation().toString();
			Gene orf = new Gene(gene.getBlockSet(), cdsStart, cdsEnd, orfName);
			rtrn.addAnnotation(orf);
		}
		return rtrn;
	}
	
	/**
	 * Check whether a codon is a stop codon (case insensitive)
	 * @param currentCodon The codon sequence as a string
	 * @return True iff the string is a stop codon
	 */
	public static boolean isStopCodon(String currentCodon) {
		boolean isStopCodon = false;
		for(String stop : STOP_CODONS) {
			if(currentCodon.equalsIgnoreCase(stop)) {
				isStopCodon = true;
				break;
			}
		}
		return isStopCodon;
	}
	
	/**
	 * Get all ORFs for genes in the bed file
	 * @param bedFile Bed file
	 * @return Collection of ORFs for all genes
	 * @throws IOException
	 */
	private AnnotationCollection<Gene> getAllORFsForGenes(String bedFile) throws IOException {
		AnnotationCollection<Gene> genes = BEDFileIO.loadFromFile(bedFile, referenceSizeFile);
		FeatureCollection<Gene> rtrn = new FeatureCollection<Gene>(coordSpace);
		Iterator<Gene> iter = genes.sortedIterator();
		while(iter.hasNext()) {
			Gene gene = iter.next();
			AnnotationCollection<Gene> orfs = getAllORFs(gene);
			Iterator<Gene> orfIter = orfs.sortedIterator();
			while(orfIter.hasNext()) {
				rtrn.add(orfIter.next());
			}
		}
		return rtrn;
	}
	
	/**
	 * Write a bed file of all ORFs for genes in the bed file
	 * @param inputBed Input bed file
	 * @param outputBed Bed file to write
	 * @throws IOException
	 */
	private void writeORFsToFile(String inputBed, String outputBed) throws IOException {
		logger.info("");
		logger.info("Writing all ORFs for genes in " + inputBed + " to file " + outputBed + "...");
		AnnotationCollection<Gene> orfs = getAllORFsForGenes(inputBed);
		BEDFileIO.writeToFile(orfs, outputBed);
		logger.info("Wrote " + orfs.getNumAnnotations() + " ORFs.");
	}
	
	/**
	 * Get all open reading frames of a sequence
	 * @param sequence The nucleotide sequence
	 * @return Collection of int[] objects containing start and end coordinates of ORFs
	 */
	public static Collection<int[]> findAllORFs(Sequence sequence) {
		String seqString = sequence.getSequenceBases();
		Collection<int[]> allORFs=new ArrayList<int[]>();
		Matcher m = START_CODON.matcher(seqString);
		while(m.find()) {
			// Check each start codon
			int startCodonPos = m.start();
			int thisORFEnd = startCodonPos;
			boolean foundStopCodon = false;
			while(thisORFEnd < seqString.length() - 3  && !foundStopCodon) {
				// Go up to first stop codon
				String currentCodon = seqString.substring(thisORFEnd, thisORFEnd+3);
				thisORFEnd += 3;
				foundStopCodon = isStopCodon(currentCodon);
			}
			if(foundStopCodon){
				int[] pos={startCodonPos, thisORFEnd};
				allORFs.add(pos);
			}
		}
		return allORFs;
	}
	
	public static void main(String[] args) throws IOException {
		
		CommandLineParser p = new CommandLineParser();
		p.addStringArg("-i", "Input bed file", true);
		p.addStringArg("-o", "Output bed file of ORFs to write", true);
		p.addStringArg("-c", "Chromosome size file", true);
		p.addStringArg("-g", "Genome fasta", true);
		p.parse(args);
		String inputBed = p.getStringArg("-i");
		String outputBed = p.getStringArg("-o");
		String chrSizeFile = p.getStringArg("-c");
		String genomeFasta = p.getStringArg("-g");
		
		ORFFinder of = new ORFFinder(genomeFasta, chrSizeFile);
		of.writeORFsToFile(inputBed, outputBed);

	}

}
