package es.fdi.iw.controller;

import org.owasp.encoder.Encode;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.support.PagedListHolder;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import es.fdi.iw.ContextInitializer;
import es.fdi.iw.model.Asignatura;
import es.fdi.iw.model.Categoria;
import es.fdi.iw.model.MensajeModeracion;
import es.fdi.iw.model.Usuario;
import es.fdi.iw.model.Votacion;
import io.netty.handler.codec.http.HttpResponse;
import scala.annotation.meta.setter;

// entityManager.find(Usuario,id)
// Reescribir en la sesion
// Session set (atribute Usuario u)
// @ModelAttribute("user")

@Controller
public class HomeController {

	private static final Logger logger = LoggerFactory
			.getLogger(HomeController.class);

	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * Intercepts login requests generated by the header; then continues to load
	 * normal page
	 */
	@RequestMapping(value = "/login", method = RequestMethod.POST)
	@Transactional
	public String login(@RequestParam("email") String formLogin,
			@RequestParam("pass") String formPass,
			@RequestParam("source") String formSource,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session ,@RequestParam("csrf") String token) {
		
		if(!isTokenValid(session, token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	
		// validate request
		if (formLogin == null || formLogin.length() < 4 || formPass == null
				|| formPass.length() < 4) {
			model.addAttribute("loginError",
					"usuarios y contraseñas: 4 caracteres mínimo");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		} else {
			Usuario u = null;
			try {
				u = (Usuario) entityManager.createNamedQuery("usuarioLogin")
						.setParameter("loginParam", formLogin)
						.getSingleResult();
				logger.info("Usuario " + u.toString());
				if (u.esLoginValido(formPass)) {
					logger.info("pass was valid");
					session.setAttribute("user", u);
					// sets the anti-csrf token
					if(isAdmin(session)){
						getTokenForSession(session);
						return "redirect:admin";
					}
					else{
						getTokenForSession(session);
						return "redirect:home";
					}
				} else {
					logger.info("pass was NOT valid");
					model.addAttribute("loginError",
							"error en usuario o contraseña");
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

				}
			} catch (NoResultException nre) {
				if (formPass.length() == 4) {
				} else {
					logger.info("no such login: {}", formLogin);
				}
				model.addAttribute("loginError",
						"error en usuario o contraseña");
			}
		}

		// redirects to view from which login was requested
		return "redirect:" + formSource;
	}

	/**
	 * Delete a user; return JSON indicating success or failure
	 */
	@RequestMapping(value = "/delUser", method = RequestMethod.POST)
	@ResponseBody
	@Transactional
	// needed to allow DB change
	public ResponseEntity<String> bookAuthors(@RequestParam("id") long id,
			@RequestParam("csrf") String token, HttpSession session) {
		if (!isAdmin(session) || !isTokenValid(session, token)) {
			return new ResponseEntity<String>(
					"Error: no such user or bad auth", HttpStatus.FORBIDDEN);
		} else if (entityManager.createNamedQuery("borrarUsuario")
				.setParameter("idParam", id).executeUpdate() == 1) {
			return new ResponseEntity<String>("Ok: user " + id + " removed",
					HttpStatus.OK);
		} else {
			return new ResponseEntity<String>("Error: no such user",
					HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Logout (also returns to home view).
	 */
	@RequestMapping(value = "/logout", method = RequestMethod.GET)
	public String logout(HttpSession session) {
		logger.info("User '{}' logged out", session.getAttribute("user"));
		session.invalidate();
		return "redirect:home";
	}

	/**
	 * Toggles debug mode
	 */
	@RequestMapping(value = "/debug", method = RequestMethod.GET)
	public String debug(HttpSession session, HttpServletRequest request) {
		String formDebug = request.getParameter("debug");
		logger.info("Setting debug to {}", formDebug);
		session.setAttribute("debug", "true".equals(formDebug) ? "true"
				: "false");
		return "redirect:/";
	}

	/**
	 * Simply selects the home view to render by returning its name.
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String empty(Locale locale, Model model) {
		logger.info("Welcome home! The client locale is {}.", locale);

		Date date = new Date();
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG,
				DateFormat.LONG, locale);

		String formattedDate = dateFormat.format(date);
		/*@RequestMapping(value="/buscarUsuario",method = RequestMethod.POST)
		public String buscarUsuario(
				@RequestParam("usuarioBusqueda") String formUsuario){
			@SuppressWarnings("unchecked")
			List<Usuario> u = (List<Usuario>)entityManager.createNamedQuery("todosUsuarios").getResultList();
			return "";
		}*/
		model.addAttribute("serverTime", formattedDate);
		model.addAttribute("pageTitle", "Inicio OmmisCracia");

		return "home";
	}

	/**
	 * Simply selects the home view to render by returning its name.
	 */
	@RequestMapping(value = "/home", method = RequestMethod.GET)
	public String index(Locale locale, Model model,HttpSession session) {
		if (isAdmin(session)){
			return "admin";
		}
		else {
			return empty(locale, model);
		}
	}

	/**
	 * A not-very-dynamic view that shows an "about us".
	 */
	@RequestMapping(value = "/about", method = RequestMethod.GET)
	@Transactional
	public String about(Locale locale, Model model) {
		logger.info("User is looking up 'about us'");
		@SuppressWarnings("unchecked")
		List<Usuario> us = (List<Usuario>) entityManager.createQuery(
				"select u from Usuario u").getResultList();
		System.err.println(us.size());
		model.addAttribute("users", us);
		model.addAttribute("pageTitle", "¿Quiénes somos?");
		return "about";
	}

	@RequestMapping(value = "/faq", method = RequestMethod.GET)
	public String faq(Locale locale, Model model) {
		model.addAttribute("pageTitle", "Preguntas frecuentes");
		return "faq";
	}

	@RequestMapping(value = "/services", method = RequestMethod.GET)
	public String services(Locale locale, Model model) {
		model.addAttribute("pageTitle", "Servicios Omniscracia");
		return "services";
	}

	/**
	 * Checks the anti-csrf token for a session against a value
	 * 
	 * @param session
	 * @param token
	 * @return the token
	 */
	static boolean isTokenValid(HttpSession session, String token) {
		Object t = session.getAttribute("csrf_token");
		return (t != null) && t.equals(token);
	}

	/**
	 * Returns true if the user is logged in and is an admin
	 */
	static boolean isAdmin(HttpSession session) {
		Usuario u = (Usuario) session.getAttribute("user");
		if (u != null) {
			return u.getRol().equals("admin");
		} else {
			return false;
		}
	}
	
	static boolean isLogged(HttpSession session){
		if(session.getAttribute("user") == null) return false;
		else return true;
	}

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String login(Model model,HttpSession session) {
		if(isLogged(session)) return "/home";
		model.addAttribute("pageTitle", "Login Omniscracia");
		return "login";
	}

	@RequestMapping(value = "/signin", method = RequestMethod.GET)
	public String signin(Model model,HttpSession session) {
		if(isLogged(session)) return "/home";
		model.addAttribute("pageTitle", "Registro Omniscracia");
		return "signin";
	}

	@RequestMapping(value = "/signin", method = RequestMethod.POST)
	@Transactional
	public String signIn(
			@RequestParam("source") String formSource,
			@RequestParam("email") String formEmail,
			@RequestParam("pass") String formPass,
			@RequestParam("firstName") String formName,
			@RequestParam("lastName") String formLastNAme,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session,@RequestParam("csrf") String token) {
		
		if(!isTokenValid(session, token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		if (formEmail == null || formEmail.length() < 4 || formPass == null
				|| formPass.length() < 4) {
			model.addAttribute("loginError",
					"usuarios y contraseñas: 4 caracteres mínimo");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return "redirect:home";
		} else {
			Usuario user = Usuario.crearUsuario(formEmail, formPass, formName, formLastNAme, "user");
			entityManager.persist(user);
			session.setAttribute("user", user);
			// sets the anti-csrf token
			getTokenForSession(session);
			return "redirect:" + formSource;
		}
	}



	@RequestMapping(value = "/mensajeModeracion{idvotacion}", method = RequestMethod.GET)
	public String mensajeModeracion(
			@PathVariable("idvotacion") String idVotacion,
			Model model) {
		model.addAttribute("pageTitle", "Moderación");
		return "mensajemoderacion";
	}

	@RequestMapping(value = "/mensajeModeracion{idvotacion}", method = RequestMethod.POST)
	@Transactional
	public String mensajeModeracion(HttpSession sesion,HttpServletResponse response,
			@PathVariable("idvotacion") String idVotacion,
			@RequestParam("mensaje") String mensajeForm,
			@RequestParam("motivo") String motivoForm,
			Model model,@RequestParam("csrf") String token) {
		
		if(!isTokenValid(sesion, token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		model.addAttribute("prefix", "./");
		Usuario u = (Usuario) sesion.getAttribute("user");
		MensajeModeracion m = new MensajeModeracion();

		if (idVotacion.isEmpty())//No tiene nada que ver con votaciones el reporte.
			m = m.crearMensajeModeracion(u.getId(),motivoForm, mensajeForm);
		else
			m = m.crearMensajeModeracion(u.getId(), Integer.parseInt(idVotacion), motivoForm, mensajeForm);
		entityManager.persist(m);
		return "home";
	}

	//{idusuario:\\d+} con \\d+ forzamos a que sea un digito.
	@RequestMapping(value = "/perfilUsuario{idusuario:\\d+}", method = RequestMethod.GET)
	@Transactional
	public String perfilUsuario(Model model,@PathVariable("idusuario") long idUsuario,HttpServletResponse response) {
		Usuario u = entityManager.find(Usuario.class, idUsuario);
		if(u == null){
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			logger.error("No se encuentra el usuario {}", idUsuario);
		}
		else
			model.addAttribute("usuarioSelec",u);
		model.addAttribute("pageTitle", "PerfilUsuario" + idUsuario);
		model.addAttribute("prefix", "./");
		return "perfilusuario";
	}

	@RequestMapping(value = "/miPerfil", method = RequestMethod.GET)
	public String miPerfil(Model model, HttpSession sesion) {
		if(!isLogged(sesion)) return "redirect:" + "/login";
		
		model.addAttribute("pageTitle", "Mi Perfil");
		
		List<Votacion> lista = null;
		Usuario u = (Usuario) sesion.getAttribute("user");
		model.addAttribute("usuarioSelec",u);
		model.addAttribute("lista", lista);
		return "miperfil";
	}

	@RequestMapping(value = "/contact", method = RequestMethod.GET)
	public String contact(Model model) {
		model.addAttribute("pageTitle", "Contáctanos");
		return "contact";
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/busquedaUsuario", method = RequestMethod.GET)
	public String busquedaUsuario(Model model,
			@RequestParam("busqueda") String formBuscar) {
		model.addAttribute("cabecera","Resultados Busqueada");
		model.addAttribute("pageTitle", "Resultado de la busqueda");
		List<Usuario> lista = null;
		lista = (List<Usuario>)entityManager.createNamedQuery("busquedaUsuario")
				.setParameter("param1", formBuscar + "%").getResultList();
		for(Usuario u:lista) logger.info(u.getEmail() + "\n");
		PagedListHolder<Usuario> pagedListHolder = new PagedListHolder<Usuario>(lista);
		pagedListHolder.setPageSize(9);
		List<Usuario> pagedList = pagedListHolder.getPageList();
		model.addAttribute("pagedListUsuarios", pagedList);
		return "usersresults";
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/mejoresAlumnos", method = RequestMethod.GET)
	public String mejoresAlumnos(Model model) {
		model.addAttribute("pageTitle", "Alumnos");
		List<Usuario> lista = null;
		lista = (List<Usuario>)entityManager
				.createNamedQuery("mejoresAlumnos").getResultList();
		PagedListHolder<Usuario> pagedListHolder = new PagedListHolder<Usuario>(lista);
		pagedListHolder.setPageSize(9);
		List<Usuario> pagedList = pagedListHolder.getPageList();
		model.addAttribute("pagedListUsuarios", pagedList);
		return "usersresults";
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/mejoresProfes", method = RequestMethod.GET)
	public String mejoresProfest(Model model) {
		model.addAttribute("pageTitle", "Profesores");
		List<Usuario> lista = null;
		lista = (List<Usuario>)entityManager.createNamedQuery("mejoresProfesores").getResultList();
		PagedListHolder<Usuario> pagedListHolder = new PagedListHolder<Usuario>(lista);
		pagedListHolder.setPageSize(9);
		List<Usuario> pagedList = pagedListHolder.getPageList();
		model.addAttribute("pagedListUsuarios", pagedList);
		return "usersresults";
	}

	@RequestMapping(value = "/realizarValoracion", method = RequestMethod.GET) //valoracion.jsp	
	public String realizarValoracion(Model model,HttpSession session) {
		if(!isLogged(session)) return "redirect:" + "/login";
		model.addAttribute("prefix", "./");
		model.addAttribute("pageTitle", "Valoración");
		return "valoracion";
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/realizarValoracion", method = RequestMethod.POST) //valoracion.jsp
	public String realizarValoracion(Model model,@RequestParam("puntuacion") String puntuacion,
			@RequestParam("categoria") String categoria,HttpSession session,
			@RequestParam("csrf") String token,HttpServletResponse response) {
		
		if(!isLogged(session) || !isTokenValid(session, token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			
		ArrayList<Categoria> lista = new ArrayList<Categoria>();
		Categoria c = new Categoria().crearCategoria(categoria, Integer.parseInt(puntuacion));
		if(session.getAttribute("valoraciones") != null)
			lista = ((ArrayList<Categoria>)session.getAttribute("valoraciones"));
		lista.add(c);
		session.setAttribute("valoraciones", lista);
		model.addAttribute("prefix","./");
		return "voto";
	}

	@RequestMapping(value = "/realizarVotacion{idusuario}", method = RequestMethod.GET) //voto.jsp
	public String realizarVotacion(Model model,@PathVariable("idusuario") String idUsuario,HttpSession session) {
	
		if(!isLogged(session)) return "redirect:" + "/login";
		
		model.addAttribute("prefix","./");
		session.setAttribute("usuarioVotacion",idUsuario);
		return "voto";
	}


	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/realizarVotacion", method = RequestMethod.POST) //voto.jsp
	@Transactional
	public String realizarVotacion(Model model,HttpSession session,HttpServletResponse response,
			@RequestParam("comentario") String comentario,@RequestParam("csrf") String token) {
		
		if(!isTokenValid(session, token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		model.addAttribute("prefix","./");
		long idEmisor = ((Usuario)session.getAttribute("user")).getId();
		Integer idUsuarioVotacion = Integer.parseInt((String)session.getAttribute("usuarioVotacion"));
		ArrayList<Categoria> lista = new ArrayList<Categoria>();
		lista = (ArrayList<Categoria>) session.getAttribute("valoraciones");
		Votacion v = new Votacion(); 
		v = v.crearVotacion(idEmisor,idUsuarioVotacion,lista,comentario);
		entityManager.persist(v);
		session.removeAttribute("valoraciones");
		session.removeAttribute("usuarioVotacion");
		return "home";
	}

	@RequestMapping(value = "/mostrarVotacionesRealizadas{idusuario:\\d+}", method = RequestMethod.GET)
	public String mostrarVotacionesRealizadas(Model model,@PathVariable("idusuario") long idUsuario,HttpSession session) {
		model.addAttribute("cabecera","Valoraciones Realizadas");
		model.addAttribute("prefix","./");
		List<Votacion> emitidas = null;
		emitidas = (List<Votacion>) entityManager.createNamedQuery("buscarVotacionesRealizadas")
				.setParameter("param1", idUsuario).getResultList();
		session.setAttribute("usuarioVotacion",idUsuario);
		model.addAttribute("votaciones",emitidas);
		return "votaciones";
	}
	
	@Transactional
	@RequestMapping(value = "/mostrarVotacionesRecibidas{idusuario:\\d+}", method = RequestMethod.GET)
	public String mostrarVotacionesRecibidas(Model model,@PathVariable("idusuario") long idUsuario,HttpSession session) {
		model.addAttribute("cabecera","Valoraciones Recibidas");
		model.addAttribute("prefix","./");
		List<Votacion> recibidas = null;
		recibidas = (List<Votacion>) entityManager.createNamedQuery("buscarVotacionesRecibidas")
				.setParameter("param1", idUsuario).getResultList();
		model.addAttribute("usuarioVotacion",idUsuario);
		model.addAttribute("votaciones",recibidas);
		return "votaciones";
	}
	
	@RequestMapping(value = "/mostrarAsignaturas{idusuario}", method = RequestMethod.GET)
	public String mostrarAsignaturas(Model model,@PathVariable("idusuario") String idUsuario,HttpSession session) {
		model.addAttribute("prefix","./");
		session.setAttribute("usuarioVotacion",idUsuario);
		return "home";
	}
	
	/* ***Parte de la vista del admin*/

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/admin", method = RequestMethod.GET)
	public String admin(Model model,HttpSession session,HttpServletResponse response) {
		
		if(!isLogged(session)) return "redirect:login";
		
		if(!isAdmin(session)){
			logout(session);
			return "redirect:login";
		}

		List<Asignatura> asignaturas= null;
		asignaturas = (List<Asignatura>)entityManager
				.createNamedQuery("todasAsignaturas").getResultList();
		model.addAttribute("TodasAsignaturas",asignaturas);
		List<Votacion> votaciones= null;
		votaciones = (List<Votacion>)entityManager
				.createNamedQuery("todasVotaciones").getResultList();
		model.addAttribute("todasVotaciones",votaciones);
		List<Usuario> usuarios = null;
		usuarios = (List<Usuario>)entityManager.createNamedQuery("todosUsuarios").getResultList();
		model.addAttribute("todosUsuarios",usuarios);
		List<MensajeModeracion> mensajes = null;
		mensajes = (List<MensajeModeracion>)entityManager.createNamedQuery("todosMensajesModeracion").getResultList();
		model.addAttribute("todosMensajes",mensajes);
		return "admin";
		/****************poner un boolean para saber si el administrador a leido el mensaje o no y mostrarlo en la tabla***********************/
	}

	@RequestMapping(value = "/adminAddUser", method = RequestMethod.POST)
	@Transactional
	public String admin2(@RequestParam("source") String formSource,
			@RequestParam("email") String formEmail,
			@RequestParam("pass") String formPass,
			@RequestParam("firstName") String formName,
			@RequestParam("lastName") String formLastNAme,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session,@RequestParam("csrf") String token) {
		
		if(!isAdmin(session) || !isTokenValid(session,token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		if (formEmail == null || formEmail.length() < 4 || formPass == null
				|| formPass.length() < 4) {
			model.addAttribute("loginError",
					"usuarios y contraseñas: 4 caracteres mínimo");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

		} else {
			Usuario user = Usuario.crearUsuario(formEmail, formPass, formName, formLastNAme, "user");
			entityManager.persist(user);
			session.setAttribute("user", user);
			// sets the anti-csrf token
			getTokenForSession(session);

		}
		return "redirect:" + formSource;
	}

	
	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/adminDeleteUser", method = RequestMethod.POST)
	@Transactional
	public String adminDeleteUser(@RequestParam("source") String formSource,
			@RequestParam("Id") long formId,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session,@RequestParam("csrf") String token) {
	
		if(!isAdmin(session) || !isTokenValid(session,token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		entityManager.createNamedQuery("borrarUsuario")
		.setParameter("idParam", formId).executeUpdate();
		List<Usuario> usuarios = null;
		usuarios = (List<Usuario>)entityManager.createNamedQuery("todosUsuarios").getResultList();
		model.addAttribute("todosUsuarios",usuarios);
			// sets the anti-csrf token
		//getTokenForSession(session);		
		return "redirect:" + formSource;
	}
	
	
	
	@RequestMapping(value = "/adminAddAsignatura", method = RequestMethod.POST)
	@Transactional
	public String admin3(@RequestParam("source") String formSource,
			@RequestParam("Asignatura") String formAsignatura,
			@RequestParam("Curso") String formCurso,
			@RequestParam("Anio") int formAnio,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session) {
		
		if(!isAdmin(session) || !isTokenValid(session,getTokenForSession(session)))
			return "redirect:" + "/error";
		
		Asignatura asig = Asignatura.crearAsignatura(formAsignatura, formCurso, formAnio);
		entityManager.persist(asig);
		session.setAttribute("admin", asig);
		return "redirect:" + formSource;
	}


	@RequestMapping(value = "/adminDeleteAsignatura", method = RequestMethod.POST)
	@Transactional
	public String adminDeleteAsignatura(@RequestParam("source") String formSource,
			@RequestParam("Asignatura") String formAsignatura,
			@RequestParam("Curso") String formCurso,
			@RequestParam("Anio") int formAnio,
			@RequestParam("Id") long formId,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session,@RequestParam("csrf") String token) {
		
		if(!isAdmin(session) || !isTokenValid(session,token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		Asignatura asig = Asignatura.crearAsignatura(formId,formAsignatura, formCurso, formAnio);
		logger.info("Borrando asignatura '{}' de ID '{}'", formAsignatura,formId);
		entityManager.createNamedQuery("borrarAsignatura")
		.setParameter("idParam", formId).executeUpdate();
		List<Asignatura> asignaturas= null;
		asignaturas = (List<Asignatura>)entityManager
				.createNamedQuery("todasAsignaturas").getResultList();
		model.addAttribute("TodasAsignaturas",asignaturas);
		/*la tabla no se actualiza*/
		/*por alguna razon se queda bloqueado y no sigue ejecutando */
		return "redirect:" + formSource;
	}
	
	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/adminEditAsignatura", method = RequestMethod.POST)
	@Transactional
	public String adminEditAsignatura(@RequestParam("source") String formSource,
			@RequestParam("Asignatura") String formAsignatura,
			@RequestParam("Curso") String formCurso,
			@RequestParam("Anio") int formAnio,
			@RequestParam("Id") long formId,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session,@RequestParam("csrf") String token) {
		
		if(!isAdmin(session) || !isTokenValid(session,token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

		entityManager.createNamedQuery("editarAsignatura")
		.setParameter("idParam", formId).setParameter("formAsignatura", formAsignatura)
		.setParameter("formCurso",formCurso).setParameter("formAnio",formAnio).executeUpdate();
		List<Asignatura> asignaturas= null;
		asignaturas = (List<Asignatura>)entityManager
				.createNamedQuery("todasAsignaturas").getResultList();
		model.addAttribute("TodasAsignaturas",asignaturas);
		/*la tabla no se actualiza*/
		/*por alguna razon se queda bloqueado y no sigue ejecutando */
		return "redirect:" + formSource;
	}
	
	@SuppressWarnings("unchecked")
	@ResponseBody
	@RequestMapping(value = "/adminDeleteVotacion", method = RequestMethod.POST)
	@Transactional
	public String adminDeleteVotacion(@RequestParam("source") String formSource,
			@RequestParam("Id") long formId,
			HttpServletRequest request, HttpServletResponse response,
			Model model, HttpSession session,@RequestParam("csrf") String token) {
		
		if(!isAdmin(session) || !isTokenValid(session,token)) response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		entityManager.createNamedQuery("borrarVotacion")
		.setParameter("idParam", formId).executeUpdate();
		List<Votacion> votaciones= null;
		votaciones = (List<Votacion>)entityManager
				.createNamedQuery("todasVotaciones").getResultList();
		model.addAttribute("todasVotaciones",votaciones);	
		return "redirect:" + formSource;
	}
	
	static String getTokenForSession (HttpSession session) {
		if(session.getAttribute("csrf_token") != null){
			return session.getAttribute("csrf_token").toString();
		}
		
	    String token=UUID.randomUUID().toString();
	    session.setAttribute("csrf_token", token);
	    return token;
	}
	

}


