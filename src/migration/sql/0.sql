--
-- PostgreSQL database dump
--

-- Dumped from database version 10.10 (Ubuntu 10.10-0ubuntu0.18.04.1)
-- Dumped by pg_dump version 10.10 (Ubuntu 10.10-0ubuntu0.18.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner:
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner:
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: adgroup; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.adgroup (
                                _id character varying(36) NOT NULL,
                                _rev character varying(255),
                                schema character varying(255),
                                type character varying(255),
                                name character varying(255)
);


ALTER TABLE public.adgroup OWNER TO tepid;

--
-- Name: course; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.course (
                               _id character varying(36) NOT NULL,
                               _rev character varying(255),
                               schema character varying(255),
                               type character varying(255),
                               name character varying(255),
                               season integer,
                               year integer NOT NULL
);


ALTER TABLE public.course OWNER TO tepid;

--
-- Name: destinationticket; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.destinationticket (
                                          _id character varying(36) NOT NULL,
                                          _rev character varying(255),
                                          schema character varying(255),
                                          type character varying(255),
                                          reason character varying(255),
                                          reported bigint NOT NULL,
                                          up boolean NOT NULL,
                                          activesince bigint NOT NULL,
                                          colorprinting boolean NOT NULL,
                                          displayname character varying(255),
                                          email character varying(255),
                                          faculty character varying(255),
                                          givenname character varying(255),
                                          jobexpiration bigint NOT NULL,
                                          lastname character varying(255),
                                          longuser character varying(255),
                                          middlename character varying(255),
                                          nick character varying(255),
                                          preferredname character varying(255),
                                          realname character varying(255),
                                          role character varying(255),
                                          salutation character varying(255),
                                          shortuser character varying(255),
                                          studentid integer NOT NULL
);


ALTER TABLE public.destinationticket OWNER TO tepid;

--
-- Name: fulldestination; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulldestination (
                                        _id character varying(36) NOT NULL,
                                        _rev character varying(255),
                                        schema character varying(255),
                                        type character varying(255),
                                        domain character varying(255),
                                        name character varying(255),
                                        password character varying(255),
                                        path character varying(255),
                                        ppm integer NOT NULL,
                                        protocol character varying(255),
                                        up boolean NOT NULL,
                                        username character varying(255),
                                        ticket__id character varying(36)
);


ALTER TABLE public.fulldestination OWNER TO tepid;

--
-- Name: fullsession; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fullsession (
                                    _id character varying(36) NOT NULL,
                                    _rev character varying(255),
                                    schema character varying(255),
                                    type character varying(255),
                                    expiration bigint NOT NULL,
                                    persistent boolean NOT NULL,
                                    role character varying(255),
                                    user__id character varying(36)
);


ALTER TABLE public.fullsession OWNER TO tepid;

--
-- Name: fulluser; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulluser (
                                 _id character varying(36) NOT NULL,
                                 _rev character varying(255),
                                 schema character varying(255),
                                 type character varying(255),
                                 activesince bigint NOT NULL,
                                 colorprinting boolean NOT NULL,
                                 displayname character varying(255),
                                 email character varying(255),
                                 faculty character varying(255),
                                 givenname character varying(255),
                                 jobexpiration bigint NOT NULL,
                                 lastname character varying(255),
                                 longuser character varying(255),
                                 middlename character varying(255),
                                 nick character varying(255),
                                 preferredname character varying(255),
                                 realname character varying(255),
                                 role character varying(255),
                                 salutation character varying(255),
                                 shortuser character varying(255),
                                 studentid integer NOT NULL
);


ALTER TABLE public.fulluser OWNER TO tepid;

--
-- Name: fulluser_adgroup; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulluser_adgroup (
                                         fulluser__id character varying(36) NOT NULL,
                                         groups__id character varying(36) NOT NULL
);


ALTER TABLE public.fulluser_adgroup OWNER TO tepid;

--
-- Name: fulluser_course; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulluser_course (
                                        fulluser__id character varying(36) NOT NULL,
                                        courses__id character varying(36) NOT NULL
);


ALTER TABLE public.fulluser_course OWNER TO tepid;

--
-- Name: fulluser_courses; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulluser_courses (
                                         fulluser__id character varying(36) NOT NULL,
                                         name character varying(255),
                                         season integer,
                                         year integer NOT NULL
);


ALTER TABLE public.fulluser_courses OWNER TO tepid;

--
-- Name: fulluser_groups; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulluser_groups (
                                        fulluser__id character varying(36) NOT NULL,
                                        name character varying(255)
);


ALTER TABLE public.fulluser_groups OWNER TO tepid;

--
-- Name: marqueedata; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.marqueedata (
                                    _id character varying(36) NOT NULL,
                                    _rev character varying(255),
                                    schema character varying(255),
                                    type character varying(255),
                                    title character varying(255)
);


ALTER TABLE public.marqueedata OWNER TO tepid;

--
-- Name: marqueedata_entry; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.marqueedata_entry (
                                          marqueedata__id character varying(36) NOT NULL,
                                          entry character varying(255)
);


ALTER TABLE public.marqueedata_entry OWNER TO tepid;

--
-- Name: printjob; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.printjob (
                                 _id character varying(36) NOT NULL,
                                 _rev character varying(255),
                                 schema character varying(255),
                                 type character varying(255),
                                 colorpages integer NOT NULL,
                                 deletedataon bigint NOT NULL,
                                 destination character varying(255),
                                 error character varying(255),
                                 eta bigint NOT NULL,
                                 failed bigint NOT NULL,
                                 file character varying(255),
                                 isrefunded boolean NOT NULL,
                                 name character varying(255),
                                 originalhost character varying(255),
                                 pages integer NOT NULL,
                                 printed bigint NOT NULL,
                                 processed bigint NOT NULL,
                                 queuename character varying(255),
                                 received bigint NOT NULL,
                                 started bigint NOT NULL,
                                 useridentification character varying(255)
);


ALTER TABLE public.printjob OWNER TO tepid;

--
-- Name: printqueue; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.printqueue (
                                   _id character varying(36) NOT NULL,
                                   _rev character varying(255),
                                   schema character varying(255),
                                   type character varying(255),
                                   defaulton character varying(255),
                                   loadbalancer character varying(255),
                                   name character varying(255)
);


ALTER TABLE public.printqueue OWNER TO tepid;

--
-- Name: printqueue_destinations; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.printqueue_destinations (
                                                printqueue__id character varying(36) NOT NULL,
                                                destinations character varying(255)
);


ALTER TABLE public.printqueue_destinations OWNER TO tepid;

--
-- Name: adgroup adgroup_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.adgroup
    ADD CONSTRAINT adgroup_pkey PRIMARY KEY (_id);


--
-- Name: course course_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (_id);


--
-- Name: destinationticket destinationticket_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.destinationticket
    ADD CONSTRAINT destinationticket_pkey PRIMARY KEY (_id);


--
-- Name: fulldestination fulldestination_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulldestination
    ADD CONSTRAINT fulldestination_pkey PRIMARY KEY (_id);


--
-- Name: fullsession fullsession_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fullsession
    ADD CONSTRAINT fullsession_pkey PRIMARY KEY (_id);


--
-- Name: fulluser_adgroup fulluser_adgroup_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_adgroup
    ADD CONSTRAINT fulluser_adgroup_pkey PRIMARY KEY (fulluser__id, groups__id);


--
-- Name: fulluser_course fulluser_course_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_course
    ADD CONSTRAINT fulluser_course_pkey PRIMARY KEY (fulluser__id, courses__id);


--
-- Name: fulluser fulluser_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser
    ADD CONSTRAINT fulluser_pkey PRIMARY KEY (_id);


--
-- Name: marqueedata marqueedata_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.marqueedata
    ADD CONSTRAINT marqueedata_pkey PRIMARY KEY (_id);


--
-- Name: printjob printjob_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.printjob
    ADD CONSTRAINT printjob_pkey PRIMARY KEY (_id);


--
-- Name: printqueue printqueue_pkey; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.printqueue
    ADD CONSTRAINT printqueue_pkey PRIMARY KEY (_id);


--
-- Name: fulluser_course uk_pk62exec9yb2iiltity0mrmv; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_course
    ADD CONSTRAINT uk_pk62exec9yb2iiltity0mrmv UNIQUE (courses__id);


--
-- Name: fulluser_adgroup uk_sqfigeg6bw4ky12wv1g2nxx0b; Type: CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_adgroup
    ADD CONSTRAINT uk_sqfigeg6bw4ky12wv1g2nxx0b UNIQUE (groups__id);

--
-- Name: fulluser_course fkaxhduig6xlnx90y0d45atm9se; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_course
    ADD CONSTRAINT fkaxhduig6xlnx90y0d45atm9se FOREIGN KEY (courses__id) REFERENCES public.course(_id);


--
-- Name: fulluser_courses fkb4krlotqfqq7oxx3q95i5hwrh; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_courses
    ADD CONSTRAINT fkb4krlotqfqq7oxx3q95i5hwrh FOREIGN KEY (fulluser__id) REFERENCES public.fulluser(_id);

--
-- Name: fulluser_adgroup fkcnqcr8qslqye3d7jiosih7ohc; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_adgroup
    ADD CONSTRAINT fkcnqcr8qslqye3d7jiosih7ohc FOREIGN KEY (groups__id) REFERENCES public.adgroup(_id);


--
-- Name: fulluser_groups fkd7o6sldgm41qrbqea4l7muk2q; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_groups
    ADD CONSTRAINT fkd7o6sldgm41qrbqea4l7muk2q FOREIGN KEY (fulluser__id) REFERENCES public.fulluser(_id);

--
-- Name: fulldestination fkf2p4kkdnf927fi1ui57m1t7rx; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulldestination
    ADD CONSTRAINT fkf2p4kkdnf927fi1ui57m1t7rx FOREIGN KEY (ticket__id) REFERENCES public.destinationticket(_id);

--
-- Name: printqueue_destinations fkn0px4nk3bhbbmpeprf8jmx9bf; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.printqueue_destinations
    ADD CONSTRAINT fkn0px4nk3bhbbmpeprf8jmx9bf FOREIGN KEY (printqueue__id) REFERENCES public.printqueue(_id);


--
-- Name: marqueedata_entry fkopfmd40rmy7kvyil3shei7y3p; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.marqueedata_entry
    ADD CONSTRAINT fkopfmd40rmy7kvyil3shei7y3p FOREIGN KEY (marqueedata__id) REFERENCES public.marqueedata(_id);

--
-- Name: fullsession fkrpitgjja572pxckjjev8o5w2g; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fullsession
    ADD CONSTRAINT fkrpitgjja572pxckjjev8o5w2g FOREIGN KEY (user__id) REFERENCES public.fulluser(_id);


--
-- PostgreSQL database dump complete
--

