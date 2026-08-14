package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;

public class LoincAttributePartMapManager extends AttributePartMapManager implements LoincScriptConstants {

	public LoincAttributePartMapManager (LoincScript ls, Map<String, Part> partMap) {
		super(ls, partMap);
	}

	protected void populateConceptReplacements() throws TermServerScriptException {
		knownReplacementMap.put(gl.getConcept("720309005 |Immunoglobulin G antibody to Streptococcus pneumoniae 43 (substance)|"), gl.getConcept("767402003 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 43 (substance)|"));
		knownReplacementMap.put(gl.getConcept("720308002 |Immunoglobulin G antibody to Streptococcus pneumoniae 34 (substance)|"), gl.getConcept("767408004 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 34 (substance)|"));
		knownReplacementMap.put(gl.getConcept("54708003 |Extended zinc insulin (substance)|"), gl.getConcept("10329000 |Zinc insulin (substance)|"));
		knownReplacementMap.put(gl.getConcept("409258004 |Hydroxocobalamin (substance)|"), gl.getConcept("1217427007 |Aquacobalamin (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("27192005 |Aminosalicylic acid (substance)|"), gl.getConcept("255666002 |Para-aminosalicylic acid (substance)|"));
		knownReplacementMap.put(gl.getConcept("250428009 |Substance with antimicrobial mechanism of action (substance)|"), gl.getConcept("419241000 |Substance with antibacterial mechanism of action (substance)|"));
		knownReplacementMap.put(gl.getConcept("119306004 |Drain device specimen (specimen)|"), gl.getConcept("1003707004 |Drain device submitted as specimen (specimen)|"));

		hardCodedTypeReplacementMap.put(gl.getConcept("410670007 |Time|"), gl.getConcept("370134009 |Time aspect|"));
	}

	@Override
	public boolean containsMappingForPartNum(String loincPartNum) {
		return partToAttributeValueMap.containsKey(loincPartNum);
	}

	protected void populateHardCodedMappings() throws TermServerScriptException {
		/*hardCodedMappings.put("LP36683-8", List.of(
				gl.getConcept("106202009 |Antigen in ABO blood group system (substance)|"),
				gl.getConcept("16951006 |Antigen in Rh blood group system (substance)")));
		hardCodedMappings.put("LP15445-7", List.of(
				gl.getConcept("259498006 |Bilirubin glucuronide (substance)|"),
				gl.getConcept("73828001 |Bilirubin-albumin complex (substance)")));
		hardCodedMappings.put("LP182450-9", List.of(
				gl.getConcept("259337002 |Calcifediol (substance"),
				gl.getConcept("67517005 |25-hydroxyergocalciferol (substance)")));*/
		hardCodedMappings.put("LP447904-6", List.of(
				gl.getConcept("685451010000100 |Measurement property (qualifier value)|")));
		hardCodedMappings.put("LP28812-3", List.of(
				gl.getConcept("814341010000101 |Free 6-acetylmorphine (substance)|")));
		hardCodedMappings.put("LP449186-8", List.of(
				gl.getConcept("814351010000104 |Immunoglobulin E antibody to Cucumis melo variety inodorus (substance)|")));
		hardCodedMappings.put("LP450035-3", List.of(
				gl.getConcept("1381338005 |Antigen of folate receptor alpha (substance)|")));
		hardCodedMappings.put("LP450030-4", List.of(
				gl.getConcept("1381442009 |Antigen of transcription factor SOX-11 (substance)|")));
		hardCodedMappings.put("LP450074-2", List.of(
				gl.getConcept("1381418005 |Antigen of mismatch repair endonuclease PMS2 (substance)|")));
		hardCodedMappings.put("LP450681-4", List.of(
				gl.getConcept("813831010000100|Aggregometer (physical object)|")));

		//2.83 specifics
		hardCodedMappings.put("LP95475-7", List.of(
				gl.getConcept("873811010000101 |Targeted gene mutation analysis technique (qualifier value)|")));  //   Targeted gene mutation analysis " +
		hardCodedMappings.put("LP452208-4", List.of(
				gl.getConcept("873801010000104 |Gene sequence variant (morphologic abnormality)|")));  //mutations
		hardCodedMappings.put("LP32421-7", List.of(
				gl.getConcept("873801010000104 |Gene sequence variant (morphologic abnormality)|")));  //mutations
		hardCodedMappings.put("LP18221-9", List.of(
				gl.getConcept("873791010000100 |Volatile substance (substance)|")));  //volatile

		//Genes
		hardCodedMappings.put("LP432663-5", List.of(
				gl.getConcept("1396291001|adenosine deaminase gene (cell structure)|"))); // ADA gene
		hardCodedMappings.put("LP265701-5", List.of(
				gl.getConcept("1396335009|G protein subunit alpha 11 gene (cell structure)|"))); // GNA11 gene
		hardCodedMappings.put("LP188487-5", List.of(
				gl.getConcept("1396348008|alanyl-tRNA synthetase 2, mitochondrial gene (cell structure)|"))); // AARS2 gene
		hardCodedMappings.put("LP40292-2", List.of(
				gl.getConcept("1396351001|ATP binding cassette subfamily A member 3 gene (cell structure)|"))); // ABCA3 gene
		hardCodedMappings.put("LP156997-1", List.of(
				gl.getConcept("1396354009|ATP binding cassette subfamily B member 1 gene (cell structure)|"))); // ABCB1 gene
		hardCodedMappings.put("LP64926-6", List.of(
				gl.getConcept("1396383007|ATP binding cassette subfamily B member 4 gene (cell structure)|"))); // ABCB4 gene
		hardCodedMappings.put("LP34966-9", List.of(
				gl.getConcept("1396386004|ATP binding cassette subfamily C member 8 gene (cell structure)|"))); // ABCC8 gene
		hardCodedMappings.put("LP71403-7", List.of(
				gl.getConcept("1396388003|ATP binding cassette subfamily D member 1 gene (cell structure)|"))); // ABCD1 gene
		hardCodedMappings.put("LP188452-9", List.of(
				gl.getConcept("1396434009|acyl-CoA dehydrogenase family member 8 gene (cell structure)|"))); // ACAD8 gene
		hardCodedMappings.put("LP36419-7", List.of(
				gl.getConcept("1396441003|acyl-CoA dehydrogenase medium chain gene (cell structure)|"))); // ACADM gene
		hardCodedMappings.put("LP31884-7", List.of(
				gl.getConcept("1396444006|acyl-CoA dehydrogenase short chain gene (cell structure)|"))); // ACADS gene
		hardCodedMappings.put("LP188453-7", List.of(
				gl.getConcept("1396447004|acyl-CoA dehydrogenase short/branched chain gene (cell structure)|"))); // ACADSB gene
		hardCodedMappings.put("LP172689-4", List.of(
				gl.getConcept("1396450001|acyl-CoA dehydrogenase very long chain gene (cell structure)|"))); // ACADVL gene
		hardCodedMappings.put("LP183583-6", List.of(
				gl.getConcept("1396453004|acetyl-CoA acetyltransferase 1 gene (cell structure)|"))); // ACAT1 gene
		hardCodedMappings.put("LP95187-8", List.of(
				gl.getConcept("1396456007|angiotensin I converting enzyme gene (cell structure)|"))); // ACE gene
		hardCodedMappings.put("LP188454-5", List.of(
				gl.getConcept("1396459000|acyl-CoA synthetase family member 3 gene (cell structure)|"))); // ACSF3 gene
		hardCodedMappings.put("LP101602-3", List.of(
				gl.getConcept("1396461009|acyl-CoA synthetase long chain family member 4 gene (cell structure)|"))); // ACSL4 gene
		hardCodedMappings.put("LP36761-2", List.of(
				gl.getConcept("1396464001|actin alpha 1, skeletal muscle gene (cell structure)|"))); // ACTA1 gene
		hardCodedMappings.put("LP61781-8", List.of(
				gl.getConcept("1396467008|activin A receptor like type 1 gene (cell structure)|"))); // ACVRL1 gene
		hardCodedMappings.put("LP430817-9", List.of(
				gl.getConcept("1396477005|ADAM metallopeptidase with thrombospondin type 1 motif 13 gene (cell structure)|"))); // ADAMTS13 gene
		hardCodedMappings.put("LP436154-1", List.of(
				gl.getConcept("1396493001|ADAM metallopeptidase with thrombospondin type 1 motif 2 gene (cell structure)|"))); // ADAMTS2 gene
		hardCodedMappings.put("LP417873-9", List.of(
				gl.getConcept("1396501005|adrenoceptor beta 2 gene (cell structure)|"))); // ADRB2 gene
		hardCodedMappings.put("LP188488-3", List.of(
				gl.getConcept("1396505001|aspartylglucosaminidase gene (cell structure)|"))); // AGA gene
		hardCodedMappings.put("LP208421-0", List.of(
				gl.getConcept("1396508004|amylo-alpha-1,6-glucosidase and 4-alpha-glucanotransferase gene (cell structure)|"))); // AGL gene
		hardCodedMappings.put("LP188486-7", List.of(
				gl.getConcept("1396516008|adenosylhomocysteinase gene (cell structure)|"))); // AHCY gene
		hardCodedMappings.put("LP136154-4", List.of(
				gl.getConcept("1396517004|alanine--glyoxylate aminotransferase gene (cell structure)|"))); // AGXT gene
		hardCodedMappings.put("LP97917-6", List.of(
				gl.getConcept("1396519001|autoimmune regulator gene (cell structure)|"))); // AIRE gene
		hardCodedMappings.put("LP99727-7", List.of(
				gl.getConcept("1396522004|5'-aminolevulinate synthase 2 gene (cell structure)|"))); // ALAS2 gene
		hardCodedMappings.put("LP208424-4", List.of(
				gl.getConcept("1396525002|aldehyde dehydrogenase 3 family member A2 gene (cell structure)|"))); // ALDH3A2 gene
		hardCodedMappings.put("LP33551-0", List.of(
				gl.getConcept("1396530003|aldolase, fructose-bisphosphate B gene (cell structure)|"))); // ALDOB gene
		hardCodedMappings.put("LP188489-1", List.of(
				gl.getConcept("1396534007|alkaline phosphatase, biomineralization associated gene (cell structure)|"))); // ALPL gene
		hardCodedMappings.put("LP190760-1", List.of(
				gl.getConcept("1396551000|alsin Rho guanine nucleotide exchange factor ALS2 gene (cell structure)|"))); // ALS2 gene
		hardCodedMappings.put("LP190762-7", List.of(
				gl.getConcept("1396560008|angiogenin gene (cell structure)|"))); // ANG gene
		hardCodedMappings.put("LP411311-6", List.of(
				gl.getConcept("1396566002|apolipoprotein A1 gene (cell structure)|"))); // APOA1 gene
		hardCodedMappings.put("LP417380-5", List.of(
				gl.getConcept("1396574001|apolipoprotein A2 gene (cell structure)|"))); // APOA2 gene
		hardCodedMappings.put("LP96191-9", List.of(
				gl.getConcept("1396582001|apolipoprotein B gene (cell structure)|"))); // APOB gene
		hardCodedMappings.put("LP19650-8", List.of(
				gl.getConcept("1396588002|apolipoprotein E gene (cell structure)|"))); // APOE gene
		hardCodedMappings.put("LP184369-9", List.of(
				gl.getConcept("1396605007|amyloid beta precursor protein gene (cell structure)|"))); // APP gene
		hardCodedMappings.put("LP35575-7", List.of(
				gl.getConcept("1396609001|aprataxin gene (cell structure)|"))); // APTX gene
		hardCodedMappings.put("LP33180-8", List.of(
				gl.getConcept("1396610006|androgen receptor gene (cell structure)|"))); // AR gene
		hardCodedMappings.put("LP33211-1", List.of(
				gl.getConcept("1396614002|arylsulfatase A gene (cell structure)|"))); // ARSA gene
		hardCodedMappings.put("LP417382-1", List.of(
				gl.getConcept("1396680004|arylsulfatase B gene (cell structure)|"))); // ARSB gene
		hardCodedMappings.put("LP34684-8", List.of(
				gl.getConcept("1396683002|aristaless related homeobox gene (cell structure)|"))); // ARX gene
		hardCodedMappings.put("LP19503-9", List.of(
				gl.getConcept("1396727008|aspartoacylase gene (cell structure)|"))); // ASPA gene
		hardCodedMappings.put("LP19656-5", List.of(
				gl.getConcept("1396730001|ATPase copper transporting beta gene (cell structure)|"))); // ATP7B gene
		hardCodedMappings.put("LP35854-6", List.of(
				gl.getConcept("1396733004|Bardet-Biedl syndrome 1 gene (cell structure)|"))); // BBS1 gene
		hardCodedMappings.put("LP96329-5", List.of(
				gl.getConcept("1396742006|butyrylcholinesterase gene (cell structure)|"))); // BCHE gene
		hardCodedMappings.put("LP61619-0", List.of(
				gl.getConcept("1396744007|BCS1 ubiquinol-cytochrome c reductase complex chaperone gene (cell structure)|"))); // BCS1L gene
		hardCodedMappings.put("LP19676-3", List.of(
				gl.getConcept("1396745008|cystathionine beta-synthase gene (cell structure)|"))); // CBS gene
		hardCodedMappings.put("LP36229-0", List.of(
				gl.getConcept("1396748005|cyclin D1 gene (cell structure)|"))); // CCND1 gene
		hardCodedMappings.put("LP19684-7", List.of(
				gl.getConcept("1396751003|CF transmembrane conductance regulator gene (cell structure)|"))); // CFTR gene
		hardCodedMappings.put("LP35632-6", List.of(
				gl.getConcept("1396754006|carnitine palmitoyltransferase 2 gene (cell structure)|"))); // CPT2 gene
		hardCodedMappings.put("LP157423-7", List.of(
				gl.getConcept("1396757004|cytochrome P450 family 1 subfamily A member 2 gene (cell structure)|"))); // CYP1A2 gene
		hardCodedMappings.put("LP28553-3", List.of(
				gl.getConcept("1396760006|cytochrome P450 family 21 subfamily A member 2 gene (cell structure)|"))); // CYP21A2 gene
		hardCodedMappings.put("LP97107-4", List.of(
				gl.getConcept("1396764002|cytochrome P450 family 2 subfamily C member 19 gene (cell structure)|"))); // CYP2C19 gene
		hardCodedMappings.put("LP40500-8", List.of(
				gl.getConcept("1396765001|cytochrome P450 family 2 subfamily C member 9 gene (cell structure)|"))); // CYP2C9 gene
		hardCodedMappings.put("LP19693-8", List.of(
				gl.getConcept("1396768004|cytochrome P450 family 2 subfamily D member 6 (gene/pseudogene) (cell structure)|"))); // CYP2D6 gene
		hardCodedMappings.put("LP173520-0", List.of(
				gl.getConcept("1396806005|cytochrome P450 family 3 subfamily A member 4 gene (cell structure)|"))); // CYP3A4 gene
		hardCodedMappings.put("LP94224-0", List.of(
				gl.getConcept("1396808006|7-dehydrocholesterol reductase gene (cell structure)|"))); // DHCR7 gene
		hardCodedMappings.put("LP193286-4", List.of(
				gl.getConcept("1396810008|dihydrolipoamide dehydrogenase gene (cell structure)|"))); // DLD gene
		hardCodedMappings.put("LP32768-1", List.of(
				gl.getConcept("1396811007|DM1 protein kinase (cell structure)|"))); // DMPK gene
		hardCodedMappings.put("LP30752-7", List.of(
				gl.getConcept("1396813005|elongator acetyltransferase complex subunit 1 (cell structure)|"))); // DYS gene
		hardCodedMappings.put("LP33214-5", List.of(
				gl.getConcept("1396866008|elastin gene (cell structure)|"))); // ELN gene
		hardCodedMappings.put("LP14459-9", List.of(
				gl.getConcept("1396869001|coagulation factor II, thrombin gene (cell structure)|"))); // F2 gene
		hardCodedMappings.put("LP19697-9", List.of(
				gl.getConcept("1396872008|coagulation factor V gene (cell structure)|"))); // F5 gene
		hardCodedMappings.put("LP19702-7", List.of(
				gl.getConcept("1396873003|coagulation factor VIII gene (cell structure)|"))); // F8 gene
		hardCodedMappings.put("LP34986-7", List.of(
				gl.getConcept("1396877002|fumarylacetoacetate hydrolase gene (cell structure)|"))); // FAH gene
		hardCodedMappings.put("LP35579-9", List.of(
				gl.getConcept("1396922007|fibrillin 1 gene (cell structure)|"))); // FBN1 gene
		hardCodedMappings.put("LP19704-3", List.of(
				gl.getConcept("1396923002|fibroblast growth factor receptor 2 gene (cell structure)|"))); // FGFR2 gene
		hardCodedMappings.put("LP19706-8", List.of(
				gl.getConcept("1396926005|fibroblast growth factor receptor 3 gene (cell structure)|"))); // FGFR3 gene
		hardCodedMappings.put("LP71420-1", List.of(
				gl.getConcept("1396928006|fukutin gene (cell structure)|"))); // FKTN gene
		hardCodedMappings.put("LP33555-1", List.of(
				gl.getConcept("1396934004|fragile X messenger ribonucleoprotein 1 gene (cell structure)|"))); // FMR1 gene
		hardCodedMappings.put("LP33147-7", List.of(
				gl.getConcept("1396938001|frataxin gene (cell structure)|"))); // FXN gene
		hardCodedMappings.put("LP64869-8", List.of(
				gl.getConcept("1396942003|glucose-6-phosphatase catalytic subunit 1 gene (cell structure)|"))); // G6PC gene
		hardCodedMappings.put("LP19710-0", List.of(
				gl.getConcept("1397017002|glucose-6-phosphate dehydrogenase gene (cell structure)|"))); // G6PD gene
		hardCodedMappings.put("LP71423-5", List.of(
				gl.getConcept("1397020005|alpha glucosidase gene (cell structure)|"))); // GAA gene
		hardCodedMappings.put("LP36032-8", List.of(
				gl.getConcept("1397023007|galactose-1-phosphate uridylyltransferase gene (cell structure)|"))); // GALT gene
		hardCodedMappings.put("LP185373-0", List.of(
				gl.getConcept("1397026004|glutaryl-CoA dehydrogenase gene (cell structure)|"))); // GCDH gene
		hardCodedMappings.put("LP33150-1", List.of(
				gl.getConcept("1397030001|gap junction protein beta 2 gene (cell structure)|"))); // GJB2 gene
		hardCodedMappings.put("LP31866-4", List.of(
				gl.getConcept("1397063008|gap junction protein beta 6 gene (cell structure)|"))); // GJB6 gene
		hardCodedMappings.put("LP31872-2", List.of(
				gl.getConcept("1397066000|galactosidase alpha gene (cell structure)|"))); // GLA gene
		hardCodedMappings.put("LP36781-0", List.of(
				gl.getConcept("1397068004|glucosamine (UDP-N-acetyl)-2-epimerase/N-acetylmannosamine kinase gene (cell structure)|"))); // GNE gene
		hardCodedMappings.put("LP19719-1", List.of(
				gl.getConcept("1397070008|hemoglobin subunit beta gene (cell structure)|"))); // HBB gene
		hardCodedMappings.put("LP30742-8", List.of(
				gl.getConcept("1397072000|hexosaminidase subunit alpha gene (cell structure)|"))); // HEXA gene
		hardCodedMappings.put("LP36037-7", List.of(
				gl.getConcept("1397073005|huntingtin gene (cell structure)|"))); // HTT gene
		hardCodedMappings.put("LP97942-4", List.of(
				gl.getConcept("1397074004|alpha-L-iduronidase gene (cell structure)|"))); // IDUA gene
		hardCodedMappings.put("LP97597-6", List.of(
				gl.getConcept("1397075003|inhibitor of nuclear factor kappa B kinase regulatory subunit gamma gene (cell structure)|"))); // IKBKG gene
		hardCodedMappings.put("LP36473-4", List.of(
				gl.getConcept("1397078001|Janus kinase 2 gene (cell structure)|"))); // JAK2 gene
		hardCodedMappings.put("LP61783-4", List.of(
				gl.getConcept("1397080007|Janus kinase 3 gene (cell structure)|"))); // JAK3 gene
		hardCodedMappings.put("LP35582-3", List.of(
				gl.getConcept("1397081006|L1 cell adhesion molecule gene (cell structure)|"))); // L1CAM gene
		hardCodedMappings.put("LP33146-9", List.of(
				gl.getConcept("1397084003|lamin A/C gene (cell structure)|"))); // LMNA gene
		hardCodedMappings.put("LP32675-8", List.of(
				gl.getConcept("1397086001|mucolipin TRP cation channel 1 gene (cell structure)|"))); // MCOLN1 gene
		hardCodedMappings.put("LP63483-9", List.of(
				gl.getConcept("1397088000|platelet activating factor acetylhydrolase 1b regulatory subunit 1 gene (cell structure)|"))); // MDCR gene
		hardCodedMappings.put("LP33049-5", List.of(
				gl.getConcept("1397089008|methyl-CpG binding protein 2 gene (cell structure)|"))); // MECP2 gene
		hardCodedMappings.put("LP33050-3", List.of(
				gl.getConcept("1397090004|MEFV innate immunity regulator, pyrin gene (cell structure)|"))); // MEFV gene
		hardCodedMappings.put("LP95534-1", List.of(
				gl.getConcept("1397093002|MPL proto-oncogene, thrombopoietin receptor gene (cell structure)|"))); // MPL gene
		hardCodedMappings.put("LP32673-3", List.of(
				gl.getConcept("1397094008|myelin protein zero gene (cell structure)|"))); // MPZ gene
		hardCodedMappings.put("LP36232-4", List.of(
				gl.getConcept("1397098006|MYC proto-oncogene, bHLH transcription factor gene (cell structure)|"))); // MYC gene
		hardCodedMappings.put("LP36764-6", List.of(
				gl.getConcept("1397101005|nebulin gene (cell structure)|"))); // NEB gene
		hardCodedMappings.put("LP186170-9", List.of(
				gl.getConcept("1397114006|NPC intracellular cholesterol transporter 1 gene (cell structure)|"))); // NPC1 gene
		hardCodedMappings.put("LP31878-9", List.of(
				gl.getConcept("1397117004|NPHS1 adhesion molecule, nephrin gene (cell structure)|"))); // NPHS1 gene
		hardCodedMappings.put("LP101430-9", List.of(
				gl.getConcept("1397118009|NPHS2 stomatin family member, podocin gene (cell structure)|"))); // NPHS2 gene
		hardCodedMappings.put("LP89631-3", List.of(
				gl.getConcept("1397120007|nucleophosmin 1 gene (cell structure)|"))); // NPM1 gene
		hardCodedMappings.put("LP63593-5", List.of(
				gl.getConcept("1397123009|phenylalanine hydroxylase gene (cell structure)|"))); // PAH gene
		hardCodedMappings.put("LP189384-3", List.of(
				gl.getConcept("1397126001|protocadherin related 15 gene (cell structure)|"))); // PCDH15 gene
		hardCodedMappings.put("LP95531-7", List.of(
				gl.getConcept("1397127005|proprotein convertase subtilisin/kexin type 9 gene (cell structure)|"))); // PCSK9 gene
		hardCodedMappings.put("LP32668-3", List.of(
				gl.getConcept("1397129008|palmitoyl-protein thioesterase 1 gene (cell structure)|"))); // PPT1 gene
		hardCodedMappings.put("LP33552-8", List.of(
				gl.getConcept("1397130003|PROP paired-like homeobox 1 gene (cell structure)|"))); // PROP1 gene
		hardCodedMappings.put("LP33143-6", List.of(
				gl.getConcept("1397132006|glycogen phosphorylase, muscle associated gene (cell structure)|"))); // PYGM gene
		hardCodedMappings.put("LP34978-4", List.of(
				gl.getConcept("1397136009|retinoschisin 1 gene (cell structure)|"))); // RS1 gene
		hardCodedMappings.put("LP35863-7", List.of(
				gl.getConcept("1397139002|sodium voltage-gated channel alpha subunit 1 gene (cell structure)|"))); // SCN1A gene
		hardCodedMappings.put("LP121197-0", List.of(
				gl.getConcept("1397140000|septin 9 gene (cell structure)|"))); // SEPT9 gene
		hardCodedMappings.put("LP40293-0", List.of(
				gl.getConcept("1397142008|sarcoglycan alpha gene (cell structure)|"))); // SGCA gene
		hardCodedMappings.put("LP61788-3", List.of(
				gl.getConcept("1397144009|sarcoglycan beta gene (cell structure)|"))); // SGCB gene
		hardCodedMappings.put("LP189692-9", List.of(
				gl.getConcept("1397145005|sucrase-isomaltase gene (cell structure)|"))); // SI gene
		hardCodedMappings.put("LP31861-5", List.of(
				gl.getConcept("1397146006|solute carrier family 67 member 1 gene (cell structure)|"))); // SLC22A18 gene
		hardCodedMappings.put("LP101445-7", List.of(
				gl.getConcept("1397149004|solute carrier family 22 member 5 gene (cell structure)|"))); // SLC22A5 gene
		hardCodedMappings.put("LP35578-1", List.of(
				gl.getConcept("1397151000|solute carrier family 26 member 4 gene (cell structure)|"))); // SLC26A4 gene
		hardCodedMappings.put("LP65333-4", List.of(
				gl.getConcept("1397154008|solute carrier family 6 member 4 gene (cell structure)|"))); // SLC6A4 gene
		hardCodedMappings.put("LP33177-4", List.of(
				gl.getConcept("1397157001|survival of motor neuron 1, telomeric gene (cell structure)|"))); // SMN1 gene
		hardCodedMappings.put("LP64868-0", List.of(
				gl.getConcept("1397158006|survival of motor neuron 2, centromeric gene (cell structure)|"))); // SMN2 gene
		hardCodedMappings.put("LP30751-9", List.of(
				gl.getConcept("1397159003|sphingomyelin phosphodiesterase 1 gene (cell structure)|"))); // SMPD1 gene
		hardCodedMappings.put("LP31889-6", List.of(
				gl.getConcept("1397160008|serine peptidase inhibitor Kazal type 1 gene (cell structure)|"))); // SPINK1 gene
		hardCodedMappings.put("LP33010-7", List.of(
				gl.getConcept("1397162000|sex determining region Y gene (cell structure)|"))); // SRY gene
		hardCodedMappings.put("LP61790-9", List.of(
				gl.getConcept("1397197003|transforming growth factor beta receptor 1 gene (cell structure)|"))); // TGFBR1 gene
		hardCodedMappings.put("LP61791-7", List.of(
				gl.getConcept("1397200002|transforming growth factor beta receptor 2 gene (cell structure)|"))); // TGFBR2 gene
		hardCodedMappings.put("LP35573-2", List.of(
				gl.getConcept("1397202005|tyrosine hydroxylase gene (cell structure)|"))); // TH gene
		hardCodedMappings.put("LP97598-4", List.of(
				gl.getConcept("1397203000|TNF receptor superfamily member 13B gene (cell structure)|"))); // TNFRSF13B gene
		hardCodedMappings.put("LP33553-6", List.of(
				gl.getConcept("1397206008|thiopurine S-methyltransferase gene (cell structure)|"))); // TPMT gene
		hardCodedMappings.put("LP33158-4", List.of(
				gl.getConcept("1397209001|transthyretin gene (cell structure)|"))); // TTR gene
		hardCodedMappings.put("LP31870-6", List.of(
				gl.getConcept("1397211005|UDP glucuronosyltransferase family 1 member A1 gene (cell structure)|"))); // UGT1A1 gene
		hardCodedMappings.put("LP65679-0", List.of(
				gl.getConcept("1397214002|vitamin K epoxide reductase complex subunit 1 gene (cell structure)|"))); // VKORC1 gene
		hardCodedMappings.put("LP62804-7", List.of(
				gl.getConcept("1397217009|vacuolar protein sorting 13 homolog B gene (cell structure)|"))); // VPS13B gene
		hardCodedMappings.put("LP35694-6", List.of(
				gl.getConcept("1397221002|von Willebrand factor gene (cell structure)|"))); // VWF gene
		hardCodedMappings.put("LP19717-5", List.of(
				gl.getConcept("1397222009|hemoglobin subunit alpha 1 gene (cell structure)|"))); // HBA1 gene
		hardCodedMappings.put("LP19724-1", List.of(
				gl.getConcept("1397224005|homeostatic iron regulator gene (cell structure)|"))); // HFE gene
		hardCodedMappings.put("LP19739-9", List.of(
				gl.getConcept("1397227003|methylenetetrahydrofolate reductase gene (cell structure)|"))); // MTHFR gene
		hardCodedMappings.put("LP19757-1", List.of(
				gl.getConcept("1397229000|peripheral myelin protein 22 gene (cell structure)|"))); // PMP22 gene
		hardCodedMappings.put("LP19759-7", List.of(
				gl.getConcept("1397232002|prosaposin gene (cell structure)|"))); // PSAP gene
		hardCodedMappings.put("LP19753-0", List.of(
				gl.getConcept("1397236004|serpin family A member 1 gene (cell structure)|"))); // SERPINA1 gene
		hardCodedMappings.put("LP19765-4", List.of(
				gl.getConcept("1397237008|synuclein alpha gene (cell structure)|"))); // SNCA gene
		hardCodedMappings.put("LP208411-1", List.of(
				gl.getConcept("1397238003|argininosuccinate synthase 1 gene (cell structure)|"))); // ASS1 gene
		hardCodedMappings.put("LP265686-8", List.of(
				gl.getConcept("1397242000|ASXL transcriptional regulator 1 gene (cell structure)|"))); // ASXL1 gene
		hardCodedMappings.put("LP135599-1", List.of(
				gl.getConcept("1397248001|ATPase Na+/K+ transporting subunit alpha 2 gene (cell structure)|"))); // ATP1A2 gene
		hardCodedMappings.put("LP188490-9", List.of(
				gl.getConcept("1397251008|ATP synthase F1 subunit alpha gene (cell structure)|"))); // ATP5A1 gene
		hardCodedMappings.put("LP32677-4", List.of(
				gl.getConcept("1397254000|ATPase copper transporting alpha gene (cell structure)|"))); // ATP7A gene
		hardCodedMappings.put("LP208413-7", List.of(
				gl.getConcept("1397257007|Bardet-Biedl syndrome 10 gene (cell structure)|"))); // BBS10 gene
		hardCodedMappings.put("LP62802-1", List.of(
				gl.getConcept("1397259005|Bardet-Biedl syndrome 2 gene (cell structure)|"))); // BBS2 gene
		hardCodedMappings.put("LP208434-3", List.of(
				gl.getConcept("1397271004|branched chain keto acid dehydrogenase E1 subunit beta gene (cell structure)|"))); // BCKDHB gene
		hardCodedMappings.put("LP36762-0", List.of(
				gl.getConcept("1397279002|folliculin gene (cell structure)|"))); // BHD gene
		hardCodedMappings.put("LP429664-8", List.of(
				gl.getConcept("1397282007|baculoviral IAP repeat containing 3 gene (cell structure)|"))); // BIRC3 gene
		hardCodedMappings.put("LP417384-7", List.of(
				gl.getConcept("1397285009|bisphosphoglycerate mutase gene (cell structure)|"))); // BPGM gene
		hardCodedMappings.put("LP19671-4", List.of(
				gl.getConcept("1397288006|calcium voltage-gated channel subunit alpha1 S gene (cell structure)|"))); // CACNA1S gene
		hardCodedMappings.put("LP71411-0", List.of(
				gl.getConcept("1397292004|solute carrier family 25 member 20 gene (cell structure)|"))); // CACT gene
		hardCodedMappings.put("LP35855-3", List.of(
				gl.getConcept("1397296001|calpain 3 gene (cell structure)|"))); // CAPN3 gene
		hardCodedMappings.put("LP422604-1", List.of(
				gl.getConcept("1397299008|caspase recruitment domain family member 11 gene (cell structure)|"))); // CARD11 gene
		hardCodedMappings.put("LP94500-3", List.of(
				gl.getConcept("1397302008|caveolin 3 gene (cell structure)|"))); // CAV3 gene
		hardCodedMappings.put("LP19680-5", List.of(
				gl.getConcept("1397304009|C-C motif chemokine receptor 5 gene (cell structure)|"))); // CCR5 gene

		hardCodedMappings.put("LP38359-3", List.of(
				gl.getConcept("120993002 |Human herpes simplex virus antigen (substance)|"))); // Herpes simplex virus Ag
		hardCodedMappings.put("LP7589-7", List.of(
				gl.getConcept("258473003 |Semen sample (specimen)|"))); // Semen

		/*hardCodedMappings.put("LP422605-8", List.of(
				gl.getConcept("1399566001|CD79a molecule gene (cell structure)|"))); // CD79A gene
		hardCodedMappings.put("LP422606-6", List.of(
				gl.getConcept("1399567005|CD79b molecule gene (cell structure)|"))); // CD79B gene
		hardCodedMappings.put("LP189386-8", List.of(
				gl.getConcept("1399568000|cadherin related family member 1 gene (cell structure)|"))); // CDHR1 gene
		hardCodedMappings.put("LP63487-0", List.of(
				gl.getConcept("1399569008|cyclin dependent kinase like 5 gene (cell structure)|"))); // CDKL5 gene
		hardCodedMappings.put("LP19647-4", List.of(
				gl.getConcept("1399570009|cyclin dependent kinase inhibitor 2B gene (cell structure)|"))); // CDKN2B gene
		hardCodedMappings.put("LP189385-0", List.of(
				gl.getConcept("1399571008|CERK like autophagy regulator gene (cell structure)|"))); // CERKL gene
		hardCodedMappings.put("LP66641-9", List.of(
				gl.getConcept("1399573006|complement factor H gene (cell structure)|"))); // CFH gene
		hardCodedMappings.put("LP36025-2", List.of(
				gl.getConcept("1399574000|chromodomain helicase DNA binding protein 7 gene (cell structure)|"))); // CHD7 gene
		hardCodedMappings.put("LP35856-1", List.of(
				gl.getConcept("1399590000|cysteine rich hydrophobic domain 2 gene (cell structure)|"))); // CHIC2 gene
		hardCodedMappings.put("LP36723-2", List.of(
				gl.getConcept("1399592008|NLR family pyrin domain containing 3 gene (cell structure)|"))); // CIAS1 gene
		hardCodedMappings.put("LP432665-0", List.of(
				gl.getConcept("1399593003|Cl-/H+ antiporter 7 gene (cell structure)|"))); // ClCN7 gene
		hardCodedMappings.put("LP66643-5", List.of(
				gl.getConcept("1399594009|dynein axonemal assembly factor 3 gene (cell structure)|"))); // CILD2 gene
		hardCodedMappings.put("LP33153-5", List.of(
				gl.getConcept("1399595005|ATPase plasma membrane Ca2+ transporting 3 gene (cell structure)|"))); // CLA2 gene
		hardCodedMappings.put("LP62803-9", List.of(
				gl.getConcept("1399596006|chloride voltage-gated channel 1 gene (cell structure)|"))); // CLCN1 gene
		hardCodedMappings.put("LP101390-5", List.of(
				gl.getConcept("1399597002|Cl-/H+ antiporter 5 gene (cell structure)|"))); // CLCN5 gene
		hardCodedMappings.put("LP208410-3", List.of(
				gl.getConcept("1399599004|CLN5 lysosomal BMP synthase gene (cell structure)|"))); // CLN5 gene
		hardCodedMappings.put("LP208430-1", List.of(
				gl.getConcept("1399600001|CLN8 transmembrane ER and ERGIC protein gene (cell structure)|"))); // CLN8 gene
		hardCodedMappings.put("LP35629-2", List.of(
				gl.getConcept("1399602009|CCHC-type zinc finger nucleic acid binding protein gene (cell structure)|"))); // CNBP gene
		hardCodedMappings.put("LP208418-6", List.of(
				gl.getConcept("1399607003|cyclic nucleotide gated channel subunit beta 3 gene (cell structure)|"))); // CNGB3 gene
		hardCodedMappings.put("LP61777-6", List.of(
				gl.getConcept("1399608008|cannabinoid receptor 1 gene (cell structure)|"))); // CNR1 gene
		hardCodedMappings.put("LP35857-9", List.of(
				gl.getConcept("1399609000|cochlin gene (cell structure)|"))); // COCH gene
		hardCodedMappings.put("LP71414-4", List.of(
				gl.getConcept("1399610005|collagen type X alpha 1 chain gene (cell structure)|"))); // COL10A1 gene
		hardCodedMappings.put("LP19687-0", List.of(
				gl.getConcept("1399611009|collagen type II alpha 1 chain gene (cell structure)|"))); // COL2A1 gene
		hardCodedMappings.put("LP35859-5", List.of(
				gl.getConcept("1399612002|collagen type III alpha 1 chain gene (cell structure)|"))); // COL3A1 gene
		hardCodedMappings.put("LP436149-1", List.of(
				gl.getConcept("1399613007|collagen type IV alpha 3 chain gene (cell structure)|"))); // COL4A3 gene
		hardCodedMappings.put("LP71416-9", List.of(
				gl.getConcept("1399614001|collagen type IV alpha 5 chain gene (cell structure)|"))); // COL4A5 gene
		hardCodedMappings.put("LP34681-4", List.of(
				gl.getConcept("1399615000|collagen type V alpha 1 chain gene (cell structure)|"))); // COL5A1 gene
		hardCodedMappings.put("LP436152-5", List.of(
				gl.getConcept("1401564002|coenzyme Q4 gene (cell structure)|"))); // COQ4 gene
		hardCodedMappings.put("LP36619-2", List.of(
				gl.getConcept("1401565001|cytochrome c oxidase assembly factor heme A:farnesyltransferase COX10 gene (cell structure)|"))); // COX10 gene
		hardCodedMappings.put("LP417388-8", List.of(
				gl.getConcept("1401566000|coproporphyrinogen oxidase gene (cell structure)|"))); // CPOX gene
		hardCodedMappings.put("LP71417-7", List.of(
				gl.getConcept("1401567009|carbamoyl-phosphate synthase 1 gene (cell structure)|"))); // CPS1 gene
		hardCodedMappings.put("LP32722-8", List.of(
				gl.getConcept("1401570008|cystatin B gene (cell structure)|"))); // CSTB gene
		hardCodedMappings.put("LP19689-6", List.of(
				gl.getConcept("1401571007|catenin beta 1 gene (cell structure)|"))); // CTNNB1 gene
		hardCodedMappings.put("LP208406-1", List.of(
				gl.getConcept("1401572000|cystinosin, lysosomal cystine transporter gene (cell structure)|"))); // CTNS gene
		hardCodedMappings.put("LP417390-4", List.of(
				gl.getConcept("1401669002|chymotrypsin C gene (cell structure)|"))); // CTRC gene
		hardCodedMappings.put("LP208427-7", List.of(
				gl.getConcept("1401670001|cathepsin K gene (cell structure)|"))); // CTSK gene
		hardCodedMappings.put("LP437049-2", List.of(
				gl.getConcept("1401671002|C-X-C motif chemokine receptor 4 gene (cell structure)|"))); // CXCR4 gene
		hardCodedMappings.put("LP62083-8", List.of(
				gl.getConcept("1401683000|major histocompatibility complex, class I, B gene (cell structure)|"))); // AS gene
		hardCodedMappings.put("LP62888-0", List.of(
				gl.getConcept("1401983003|cytochrome b-245 alpha chain gene (cell structure)|"))); // CYBA gene
		hardCodedMappings.put("LP97416-9", List.of(
				gl.getConcept("1401984009|cytochrome P450 family 11 subfamily B member 1 gene (cell structure)|"))); // CYP11B1 gene
		hardCodedMappings.put("LP188494-1", List.of(
				gl.getConcept("1401985005|cytochrome P450 family 17 subfamily A member 1 gene (cell structure)|"))); // CYP17A1 gene
		hardCodedMappings.put("LP157451-8", List.of(
				gl.getConcept("1401986006|cytochrome P450 family 2 subfamily B member 6 gene (cell structure)|"))); // CYP2B6 gene
		hardCodedMappings.put("LP157531-7", List.of(
				gl.getConcept("1401988007|cytochrome P450 family 2 subfamily E member 1 gene (cell structure)|"))); // CYP2E1 gene
		hardCodedMappings.put("LP281375-8", List.of(
				gl.getConcept("1402010007|cytochrome P450 family 3 subfamily A member 7 gene (cell structure)|"))); // CYP3A7 gene
		hardCodedMappings.put("LP63481-3", List.of(
				gl.getConcept("1402013009|doublecortin gene (cell structure)|"))); // DCX gene
		hardCodedMappings.put("LP189382-7", List.of(
				gl.getConcept("1402016001|whirlin gene (cell structure)|"))); // DFNB31 gene
		hardCodedMappings.put("LP436157-4", List.of(
				gl.getConcept("1402017005|dehydrodolichyl diphosphate synthase subunit gene (cell structure)|"))); // DHDDS gene
		hardCodedMappings.put("LP436151-7", List.of(
				gl.getConcept("1402018000|dynein axonemal intermediate chain 1 gene (cell structure)|"))); // DNAI1 gene
		hardCodedMappings.put("LP418788-8", List.of(
				gl.getConcept("1402019008|dynamin 2 gene (cell structure)|"))); // DNM2 gene
		hardCodedMappings.put("LP265695-9", List.of(
				gl.getConcept("1402020002|DNA methyltransferase 3 alpha gene (cell structure)|"))); // DNMT3A gene
		hardCodedMappings.put("LP36885-9", List.of(
				gl.getConcept("1402021003|dihydropyrimidine dehydrogenase gene (cell structure)|"))); // DPYD gene
		hardCodedMappings.put("LP71419-3", List.of(
				gl.getConcept("1402023000|dysferlin gene (cell structure)|"))); // DYSF gene
		hardCodedMappings.put("LP31869-8", List.of(
				gl.getConcept("1402024006|early growth response 2 gene (cell structure)|"))); // EGR2 gene
		hardCodedMappings.put("LP34968-5", List.of(
				gl.getConcept("1402025007|elastase, neutrophil expressed gene (cell structure)|"))); // ELA2 gene
		hardCodedMappings.put("LP71479-7", List.of(
				gl.getConcept("1402026008|endoglin gene (cell structure)|"))); // ENG gene
		hardCodedMappings.put("LP35094-9", List.of(
				gl.getConcept("1402028009|EPM2A glucan phosphatase, laforin gene (cell structure)|"))); // EPM2A gene
		hardCodedMappings.put("LP63482-1", List.of(
				gl.getConcept("1402030006|EYA transcriptional coactivator and phosphatase 1 gene (cell structure)|"))); // EYA1 gene
		hardCodedMappings.put("LP344998-2", List.of(
				gl.getConcept("1402044000|coagulation factor X gene (cell structure)|"))); // F10 gene
		hardCodedMappings.put("LP208566-2", List.of(
				gl.getConcept("1402045004|coagulation factor XI gene (cell structure)|"))); // F11 gene
		hardCodedMappings.put("LP99579-2", List.of(
				gl.getConcept("1402046003|coagulation factor XII gene (cell structure)|"))); // F12 gene
		hardCodedMappings.put("LP19700-1", List.of(
				gl.getConcept("1402048002|coagulation factor VII gene (cell structure)|"))); // F7 gene
		hardCodedMappings.put("LP34969-3", List.of(
				gl.getConcept("1402049005|coagulation factor IX gene (cell structure)|"))); // F9 gene
		hardCodedMappings.put("LP188495-8", List.of(
				gl.getConcept("1402050005|phenylalanyl-tRNA synthetase 2, mitochondrial gene (cell structure)|"))); // FARS2 gene
		hardCodedMappings.put("LP36438-7", List.of(
				gl.getConcept("1402051009|fibrillin 2 gene (cell structure)|"))); // FBN2 gene
		hardCodedMappings.put("LP97931-7", List.of(
				gl.getConcept("1402053007|ferrochelatase gene (cell structure)|"))); // FECH gene
		hardCodedMappings.put("LP417392-0", List.of(
				gl.getConcept("1402054001|fibrinogen alpha chain gene (cell structure)|"))); // FGA gene
		hardCodedMappings.put("LP99635-2", List.of(
				gl.getConcept("1402055000|fibrinogen beta chain gene (cell structure)|"))); // FGB gene
		hardCodedMappings.put("LP35569-0", List.of(
				gl.getConcept("1402056004|FYVE, RhoGEF and PH domain containing 1 gene (cell structure)|"))); // FGD1 gene
		hardCodedMappings.put("LP418789-6", List.of(
				gl.getConcept("1402057008|FYVE, RhoGEF and PH domain containing 4 gene (cell structure)|"))); // FGD4 gene
		hardCodedMappings.put("LP35570-8", List.of(
				gl.getConcept("1402058003|fibroblast growth factor 23 gene (cell structure)|"))); // FGF23 gene
		hardCodedMappings.put("LP36216-7", List.of(
				gl.getConcept("1402059006|fibroblast growth factor receptor 1 gene (cell structure)|"))); // FGFR1 gene
		hardCodedMappings.put("LP71421-9", List.of(
				gl.getConcept("1402060001|FIG4 phosphoinositide 5-phosphatase gene (cell structure)|"))); // FIG4 gene
		hardCodedMappings.put("LP189379-3", List.of(
				gl.getConcept("1402061002|FKBP prolyl isomerase 10 gene (cell structure)|"))); // FKBP10 gene
		hardCodedMappings.put("LP35860-3", List.of(
				gl.getConcept("1402062009|fukutin related protein gene (cell structure)|"))); // FKRP gene
		hardCodedMappings.put("LP71422-7", List.of(
				gl.getConcept("1402063004|filamin A gene (cell structure)|"))); // FLNA gene
		hardCodedMappings.put("LP420119-2", List.of(
				gl.getConcept("1402064005|forkhead box L2 gene (cell structure)|"))); // FOXL2 gene
		hardCodedMappings.put("LP200494-5", List.of(
				gl.getConcept("1402068008|follicle stimulating hormone receptor gene (cell structure)|"))); // FSHR gene
		hardCodedMappings.put("LP417394-6", List.of(
				gl.getConcept("1402069000|formimidoyltransferase cyclodeaminase gene (cell structure)|"))); // FTCD gene
		hardCodedMappings.put("LP190764-3", List.of(
				gl.getConcept("1402070004|FUS RNA binding protein gene (cell structure)|"))); // FUS gene
		hardCodedMappings.put("LP208435-0", List.of(
				gl.getConcept("1402071000|galactosylceramidase gene (cell structure)|"))); // GALC gene
		hardCodedMappings.put("LP418790-4", List.of(
				gl.getConcept("1402072007|glycyl-tRNA synthetase 1 gene (cell structure)|"))); // GARS gene
		hardCodedMappings.put("LP71424-3", List.of(
				gl.getConcept("1402073002|GTP cyclohydrolase 1 gene (cell structure)|"))); // GCH1 gene
		hardCodedMappings.put("LP71166-0", List.of(
				gl.getConcept("1402074008|glucokinase gene (cell structure)|"))); // GCK gene
		hardCodedMappings.put("LP36428-8", List.of(
				gl.getConcept("1402075009|ganglioside induced differentiation associated protein 1 gene (cell structure)|"))); // GDAP1 gene
		hardCodedMappings.put("LP61779-2", List.of(
				gl.getConcept("1402076005|glial fibrillary acidic protein gene (cell structure)|"))); // GFAP gene
		hardCodedMappings.put("LP188496-6", List.of(
				gl.getConcept("1402077001|G elongation factor mitochondrial 1 gene (cell structure)|"))); // GFM1 gene
		hardCodedMappings.put("LP32672-5", List.of(
				gl.getConcept("1402078006|gap junction protein beta 1 gene (cell structure)|"))); // GJB1 gene
		hardCodedMappings.put("LP149237-2", List.of(
				gl.getConcept("1402079003|gap junction protein beta 3 gene (cell structure)|"))); // GJB3 gene
		hardCodedMappings.put("LP61780-0", List.of(
				gl.getConcept("1402179001|glycine receptor alpha 1 gene (cell structure)|"))); // GLRA1 gene
		hardCodedMappings.put("LP265702-3", List.of(
				gl.getConcept("1402180003|G protein subunit alpha q gene (cell structure)|"))); // GNAQ gene
		hardCodedMappings.put("LP35861-1", List.of(
				gl.getConcept("1402182006|GNAS complex locus gene (cell structure)|"))); // GNAS1 gene
		hardCodedMappings.put("LP188497-4", List.of(
				gl.getConcept("1402183001|glycine N-methyltransferase gene (cell structure)|"))); // GNMT gene
		hardCodedMappings.put("LP417396-1", List.of(
				gl.getConcept("1402184007|N-acetylglucosamine-1-phosphate transferase subunits alpha and beta gene (cell structure)|"))); // GNPTAB gene
		hardCodedMappings.put("LP420049-1", List.of(
				gl.getConcept("1402185008|glucosamine (N-acetyl)-6-sulfatase gene (cell structure)|"))); // GNS gene
		hardCodedMappings.put("LP432656-9", List.of(
				gl.getConcept("1402188005|glycoprotein Ib platelet subunit beta gene (cell structure)|"))); // GP1BB gene
		hardCodedMappings.put("LP432660-1", List.of(
				gl.getConcept("1402189002|glycoprotein IX platelet gene (cell structure)|"))); // GP9 gene
		hardCodedMappings.put("LP63484-7", List.of(
				gl.getConcept("1402190006|G protein-coupled receptor 143 gene (cell structure)|"))); // GPR143 gene
		hardCodedMappings.put("LP189383-5", List.of(
				gl.getConcept("1402191005|adhesion G protein-coupled receptor V1 gene (cell structure)|"))); // GPR98 gene
		hardCodedMappings.put("LP208428-5", List.of(
				gl.getConcept("1402192003|glyoxylate and hydroxypyruvate reductase gene (cell structure)|"))); // GRHPR gene
		hardCodedMappings.put("LP207901-2", List.of(
				gl.getConcept("1402194002|granulin precursor gene (cell structure)|"))); // GRN gene
		hardCodedMappings.put("LP417398-7", List.of(
				gl.getConcept("1402195001|gelsolin gene (cell structure)|"))); // GSN gene
		hardCodedMappings.put("LP71425-0", List.of(
				gl.getConcept("1402197009|glycogen synthase 2 gene (cell structure)|"))); // GYS2 gene */
	}

}
