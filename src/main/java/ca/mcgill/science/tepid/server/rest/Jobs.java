package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.Destination;
import ca.mcgill.science.tepid.common.PrintJob;
import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.gs.GS;
import ca.mcgill.science.tepid.server.gs.GS.InkCoverage;
import ca.mcgill.science.tepid.server.util.CouchClient;
import ca.mcgill.science.tepid.server.util.QueueManager;
import ca.mcgill.science.tepid.server.util.SessionManager;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.tukaani.xz.XZInputStream;
import shared.Config;
import shared.ConfigKeys;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Path("/jobs")
public class Jobs {
	
	public static final Map<String, Thread> processingThreads = new ConcurrentHashMap<>();
	
	private final WebTarget couchdb = CouchClient.getTepidWebTarget();
	
	private static class JobResultSet extends ViewResultSet<String,PrintJob> {}
	
	@GET
	@Path("/{sam}")
	@RolesAllowed({"user", "ctfer", "elder"})
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<PrintJob> listJobs(@PathParam("sam") String sam, @Context ContainerRequestContext req) {
		Session session = (Session) req.getProperty("session");
		if (session.getRole().equals("user") && !session.getUser().shortUser.equals(sam)) {
			return null;
		}
		List<Row<String,PrintJob>> rows = couchdb.path("_design/main/_view").path("byUser").queryParam("key", "\"" + sam + "\"")
		.request(MediaType.APPLICATION_JSON).get(JobResultSet.class).rows;
		Collection<PrintJob> out = new TreeSet<>(new Comparator<PrintJob>() {
			@Override
			public int compare(PrintJob j1, PrintJob j2) {
				Date p1 = j1.getProcessed(), p2 = j2.getProcessed();
				if (j1.getFailed() != null) p1 = j1.started;
				if (j2.getFailed() != null) p2 = j2.started;
				if (p1 == null && p2 == null) return j1.started.compareTo(j2.started);
				if (p1 == null) return -1;
				if (p2 == null) return 1;
				return p2.compareTo(p1) == 0 ? j2.getId().compareTo(j1.getId()) : p2.compareTo(p1);
			}
		});
		for (Row<String,PrintJob> r : rows) {
			out.add(r.value);
		}
		return out;
	}

	@POST
	@RolesAllowed({"user", "ctfer", "elder"})
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String newJob(PrintJob j, @Context ContainerRequestContext req) {
		j.setUserIdentification(((Session) req.getProperty("session")).getUser().shortUser);
		j.setDeleteDataOn(System.currentTimeMillis() + SessionManager.getInstance().queryUserCache(j.getUserIdentification()).jobExpiration);
		System.out.println(j);
		return couchdb.request(MediaType.TEXT_PLAIN).post(Entity.entity(j, MediaType.APPLICATION_JSON)).readEntity(String.class);
	}

	@PUT
	@RolesAllowed({"user", "ctfer", "elder"})
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/{id}")
	public String addJobData(InputStream is, @PathParam("id") final String id) {
		System.out.println(id + " Receiving job data");
		File tmpDir = new File(System.getProperty("os.name").startsWith("Windows") ? System.getProperty("java.io.tmpdir") + "\\tepid" : "/tmp/tepid");
		if (!tmpDir.exists()) {
			tmpDir.mkdirs();
		}
		try {
			//write compressed job to disk
			final File tmpXz = new File(tmpDir.getAbsolutePath() + "/" + id +  ".ps.xz");
			Files.copy(is, tmpXz.toPath());
			is.close();
			//let db know we have received data
			PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
			j.setFile(tmpXz.getAbsolutePath());
			j.setReceived(new Date());
			couchdb.path(id).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
			Thread processing = new Thread("Job Processing for " + id) {
				@Override
				public void run() {
					try {
						//decompress data
						File tmp = File.createTempFile("tepid", ".ps");
						tmp.delete();
						XZInputStream decompress = new XZInputStream(new FileInputStream(tmpXz));
						Files.copy(decompress, tmp.toPath());
						//count pages
						List<InkCoverage> inkCov = GS.inkCoverage(tmp);
						int color = 0;
						for (InkCoverage i : inkCov) { 
							if (!i.monochrome) color++;
						}
						//update page count and status in db
						PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
						j.setPages(inkCov.size());
						j.setColorPages(color);
						j.setProcessed(new Date());
						System.err.println(id + " setting stats ("+inkCov.size()+" pages, "+color+" color)");
						couchdb.path(id).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
						//check if user has color printing enabled
						if (color > 0 && !SessionManager.getInstance().queryUser(j.getUserIdentification(), null).colorPrinting) {
							failJob(id, "Color disabled");
						} else {
							//check if user has sufficient quota to print this job
							if (new Users().getQuota(j.getUserIdentification()) < inkCov.size() - color + color * 3) {
								failJob(id, "Insufficient quota");
							} else {
								//add job to the queue
								j = QueueManager.assignDestination(id);
								Destination dest = couchdb.path(j.getDestination()).request(MediaType.APPLICATION_JSON).get(Destination.class);
								if (sendToSMB(tmp, dest)) {
									j.setPrinted(new Date());
									couchdb.path(id).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
									System.err.println(j.getId() + " sent to destination");
								} else {
									failJob(id, "Could not send to destination");
								}
							}
						}
						tmp.delete();
					} catch (Exception e) {
						e.printStackTrace();
						failJob(id, "Exception during processing");
					}
					processingThreads.remove(id);
				}
			};
			processingThreads.put(id, processing);
			processing.start();
			return "Job data for " + id + " successfully uploaded";
		} catch (IOException e) {
			e.printStackTrace();
			failJob(id, " Exception during upload");
		}
		

		return "Job data upload for " + id + " FAILED";
	}
	
	public void failJob(String id, String error) {
		PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
		j.setFailed(new Date(), error);
		couchdb.path(j.getId()).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
	}
	
	public static boolean sendToSMB(File f, Destination destination) {
		if (destination.getPath() == null || destination.getPath().trim().isEmpty()) {
			//this is a dummy destination
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
			}
			return true;
		}
		System.setProperty("jcifs.smb.client.useNTSmbs", "false");
		try {
			Process p = new ProcessBuilder("smbclient", "//" + destination.getPath(), destination.getPassword(), "-c",
			"print " + f.getAbsolutePath(), "-U", destination.getDomain() + "\\" + destination.getUsername()).start();
			p.waitFor();			
		} catch (IOException | InterruptedException e) {
			return false;
		}
		return true;
	}
	
//	public static boolean sendToSMB(File f, Destination destination) {
//		if (destination.getPath() == null || destination.getPath().trim().isEmpty()) {
//			//this is a dummy destination
//			try {
//				Thread.sleep(4000);
//			} catch (InterruptedException e) {
//			}
//			return true;
//		}
//		System.setProperty("jcifs.smb.client.useNTSmbs", "false");
//		try {
//			String userName = destination.getUsername(), password = destination.getPassword();
//			SmbFileOutputStream sfos = new SmbFileOutputStream(new SmbFile("smb://" + destination.getPath(), new NtlmPasswordAuthentication(destination.getDomain(), userName, password)));
//			Files.copy(f.toPath(), sfos);
//			sfos.close();
//		} catch (IOException e) {
//			return false;
//		}
//		return true;
//	}
	
	@GET
	@Path("/job/{id}/_changes")
	@RolesAllowed({"user", "ctfer", "elder"})
	@Produces(MediaType.APPLICATION_JSON)
	public void getChanges(@PathParam("id") String id, @Context UriInfo uriInfo, @Suspended AsyncResponse ar, @Context ContainerRequestContext req) {
		PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
		Session session = (Session) req.getProperty("session");
		if (session.getRole().equals("user") && !session.getUser().shortUser.equals(j.getUserIdentification())) {
			ar.resume(Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").type(MediaType.TEXT_PLAIN).build());
		}
		WebTarget target = couchdb.path("_changes").queryParam("filter", "main/byJob").queryParam("job", id);
		MultivaluedMap<String, String> qp = uriInfo.getQueryParameters();
		if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"));
		if (qp.containsKey("since")) target = target.queryParam("since", qp.getFirst("since"));
		//TODO find a way to make this truly asynchronous
		String changes = target.request().get(String.class);
		if (!ar.isDone() && !ar.isCancelled()) {
			ar.resume(changes);
		}
	}
	
	@GET
	@Path("/job/{id}")
	@RolesAllowed({"user", "ctfer", "elder"})
	@Produces(MediaType.APPLICATION_JSON)
	public Response getJob(@PathParam("id") String id, @Context UriInfo uriInfo, @Context ContainerRequestContext req) {
		PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
		Session session = (Session) req.getProperty("session");
		if (session.getRole().equals("user") && !session.getUser().shortUser.equals(j.getUserIdentification())) {
			return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").type(MediaType.TEXT_PLAIN).build();
		}
		return Response.ok(j).build();
	}
	
	@PUT
	@Path("/job/{id}/refunded")
	@RolesAllowed({"ctfer", "elder"})
	@Produces(MediaType.APPLICATION_JSON)
	public Response setJobRefunded(@PathParam("id") String id, @Context ContainerRequestContext req, boolean refunded) {
		PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
		Session session = (Session) req.getProperty("session");
		if (session.getRole().equals("ctfer") && session.getUser().shortUser.equals(j.getUserIdentification())) {
			return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot refund your own jobs").type(MediaType.TEXT_PLAIN).build();
		}
		j.setRefunded(refunded);
		couchdb.path(j.getId()).request().put(Entity.entity(j, MediaType.APPLICATION_JSON));
		return Response.ok().build();
	}
	
	@POST
	@Path("/job/{id}/reprint")
	@RolesAllowed({"user", "ctfer", "elder"})
	@Produces(MediaType.TEXT_PLAIN)
	public Response reprintJob(@PathParam("id") String id, @Context ContainerRequestContext req) {
		final PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
		if (j.getFile() == null || !new File(j.getFile()).exists()) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Data for this job no longer exists").type(MediaType.TEXT_PLAIN).build();
		}
		Session session = (Session) req.getProperty("session");
		if (session.getRole().equals("user") && !session.getUser().shortUser.equals(j.getUserIdentification())) {
			return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot reprint someone else's job").type(MediaType.TEXT_PLAIN).build();
		}
		PrintJob reprint = new PrintJob();
		reprint.setName(j.getName());
		reprint.setOriginalHost("REPRINT");
		reprint.setQueueName(j.getQueueName());
		reprint.setUserIdentification(j.getUserIdentification());
		reprint.setDeleteDataOn(System.currentTimeMillis() + SessionManager.getInstance().queryUserCache(j.getUserIdentification()).jobExpiration);
		System.out.println(reprint);
		final String newId = couchdb.request(MediaType.APPLICATION_JSON).post(Entity.entity(reprint, MediaType.APPLICATION_JSON)).readEntity(ObjectNode.class).get("id").asText();
		new Thread("Reprint " + id) {
			@Override
			public void run() {
				try {
					addJobData(new FileInputStream(new File(j.getFile())), newId);
				} catch (FileNotFoundException e) {
				}
			}
		}.start();
		return Response.ok("Reprinted " + id + " new id " + newId).build();
	}

}
