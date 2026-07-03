package com.example.connectong_api.dto;

import com.example.connectong_api.model.Ong;

import java.util.List;

/**
 * Perfil PUBLICO de uma ONG: agrega num so payload tudo que o doador ve na
 * pagina da instituicao (dados, selo, avaliacoes, campanhas, necessidades,
 * prestacoes). Reaproveita os DTOs ja existentes nas listas.
 */
public class PerfilPublicoOngDTO {

    private Long id;
    private String nome;
    private String cidade;
    private String descricao;
    private String telefone;
    private String email;
    private String cnpj;
    private boolean verificada;
    private double notaMedia;
    private int totalAvaliacoes;

    private int totalNecessidades;
    private int totalCampanhas;
    private int totalPrestacoes;

    private int transparenciaScore;     // 0-100 (Bloco 26)
    private String nivelTransparencia;  // BRONZE | PRATA | OURO

    // Perfil rico (feira): capa em base64, endereco e fotos do local.
    private String capaBase64;
    private String endereco;
    private List<String> fotosLocal;

    // Streak do ranking: dias no topo (se for o atual #1) e duracao do ultimo
    // reinado ja encerrado (se ja foi #1). Null quando nao se aplica.
    private Integer diasNoTopo;
    private Integer ultimoReinadoDias;

    private List<NecessidadeResponseDTO> necessidades;
    private List<CampanhaResponseDTO> campanhas;
    private List<AvaliacaoResponseDTO> avaliacoes;
    private List<PrestacaoResponseDTO> prestacoes;

    public PerfilPublicoOngDTO(Ong o,
                               List<NecessidadeResponseDTO> necessidades,
                               List<CampanhaResponseDTO> campanhas,
                               List<AvaliacaoResponseDTO> avaliacoes,
                               List<PrestacaoResponseDTO> prestacoes,
                               int transparenciaScore,
                               String nivelTransparencia) {
        this.transparenciaScore = transparenciaScore;
        this.nivelTransparencia = nivelTransparencia;
        this.id = o.getId();
        this.nome = o.getNome();
        this.cidade = o.getCidade();
        this.descricao = o.getDescricao();
        this.telefone = o.getTelefone();
        this.email = o.getEmail();
        this.cnpj = o.getCnpj();
        this.verificada = o.getVerificada();
        this.notaMedia = o.getNotaMedia();
        this.totalAvaliacoes = o.getTotalAvaliacoes();

        this.capaBase64 = o.getCapaBase64();
        this.endereco = o.getEndereco();
        this.ultimoReinadoDias = o.getUltimoReinadoDias();

        this.necessidades = necessidades;
        this.campanhas = campanhas;
        this.avaliacoes = avaliacoes;
        this.prestacoes = prestacoes;

        this.totalNecessidades = necessidades != null ? necessidades.size() : 0;
        this.totalCampanhas = campanhas != null ? campanhas.size() : 0;
        this.totalPrestacoes = prestacoes != null ? prestacoes.size() : 0;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getCidade() { return cidade; }
    public String getDescricao() { return descricao; }
    public String getTelefone() { return telefone; }
    public String getEmail() { return email; }
    public String getCnpj() { return cnpj; }
    public boolean isVerificada() { return verificada; }
    public double getNotaMedia() { return notaMedia; }
    public int getTotalAvaliacoes() { return totalAvaliacoes; }
    public int getTotalNecessidades() { return totalNecessidades; }
    public int getTotalCampanhas() { return totalCampanhas; }
    public int getTotalPrestacoes() { return totalPrestacoes; }
    public int getTransparenciaScore() { return transparenciaScore; }
    public String getNivelTransparencia() { return nivelTransparencia; }

    public String getCapaBase64() { return capaBase64; }
    public String getEndereco() { return endereco; }

    public List<String> getFotosLocal() { return fotosLocal; }
    public void setFotosLocal(List<String> fotosLocal) { this.fotosLocal = fotosLocal; }

    public Integer getDiasNoTopo() { return diasNoTopo; }
    public void setDiasNoTopo(Integer diasNoTopo) { this.diasNoTopo = diasNoTopo; }

    public Integer getUltimoReinadoDias() { return ultimoReinadoDias; }

    public List<NecessidadeResponseDTO> getNecessidades() { return necessidades; }
    public List<CampanhaResponseDTO> getCampanhas() { return campanhas; }
    public List<AvaliacaoResponseDTO> getAvaliacoes() { return avaliacoes; }
    public List<PrestacaoResponseDTO> getPrestacoes() { return prestacoes; }
}
