package local.conet;

/**
 * Cached content
 */
public class CachedContent {
	/** Content identifier */
	String nid = null;

	/** Chunk sequence number */
	long csn;

	/** Tag */
	long tag;

	/** Creates a new CachedContent. 
	 * @param nid
	 * @param csn
	 * @param tag 
	 */
	public CachedContent(String nid, long csn, long tag) {
		this.tag = tag;
		this.nid = nid;
		this.csn = csn;
	}

	/** Gets content identifier. */
	public String getNid() {
		return nid;
	}

	/** Gets chunk sequence number. */
	public long getCsn() {
		return csn;
	}

	/** Gets tag. */
	public long getTag() {
		return tag;
	}

}