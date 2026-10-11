package cl.duocuc.bancoxyz.batch.intereses.model;

import java.math.BigDecimal;

public class CuentaInteresProcesada {

    private Long cuentaId;
    private String nombre;
    private BigDecimal saldoInicial;
    private Integer edad;
    private String tipo;
    private BigDecimal tasaAplicada;
    private BigDecimal interesMensual;
    private BigDecimal saldoFinal;

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getTasaAplicada() {
        return tasaAplicada;
    }

    public void setTasaAplicada(BigDecimal tasaAplicada) {
        this.tasaAplicada = tasaAplicada;
    }

    public BigDecimal getInteresMensual() {
        return interesMensual;
    }

    public void setInteresMensual(BigDecimal interesMensual) {
        this.interesMensual = interesMensual;
    }

    public BigDecimal getSaldoFinal() {
        return saldoFinal;
    }

    public void setSaldoFinal(BigDecimal saldoFinal) {
        this.saldoFinal = saldoFinal;
    }

    @Override
    public String toString() {
        return "CuentaInteresProcesada{cuentaId=%s, nombre=%s, saldoInicial=%s, edad=%s, tipo=%s}"
                .formatted(cuentaId, nombre, saldoInicial, edad, tipo);
    }
}
