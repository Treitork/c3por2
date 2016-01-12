package es.fdi.iw.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

@Entity
/* Queries */
// ... //
/*
@NamedQueries({
    @NamedQuery(name="todosMensajesModeracion",
            query="select m from MENSAJE_MODERACION m order by id desc"),
})*/

/* Clase */
public class MensajeModeracion {
/* Atributos */	
private long id;
private long idEmisor;
private String motivo;
private String mensaje;


/* Constructores */
public MensajeModeracion(){}

/* Metodos */
public MensajeModeracion crearMensajeModeracion(String emisor,String mensaje,String titulo){
	MensajeModeracion m = new MensajeModeracion();
	
	m.mensaje = mensaje;;
	return m;
}

/* Getters & Setters*/
@Id
@GeneratedValue
public long getId() {
	return id;
}

public void setId(long id) {
	this.id = id;
}


public long getIdEmisor() {
	return idEmisor;
}

public void setIdEmisor(long idEmisor) {
	this.idEmisor = idEmisor;
}

public String getMotivo() {
	return motivo;
}

public void setMotivo(String motivo) {
	this.motivo = motivo;
}

public String getMensaje() {
	return mensaje;
}

public void setMensaje(String mensaje) {
	this.mensaje = mensaje;
}

}
